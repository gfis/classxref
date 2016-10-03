/*  Apache Ant task for crossreferences of Java classes
    @(#) $Id: ClassXref.java 387 2010-03-29 06:38:33Z  $
	2008-02-13: Java 1.5 types
    2008-01-10: adapted to org.teherba conventions
    2006-08-15, Georg Fischer
*/
/*
 * Copyright 2006 Dr. Georg Fischer <punctum at punctum dot kom>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tools.ant.taskdefs;
import	org.apache.tools.ant.Task;
import	org.apache.tools.ant.BuildException;
import	java.io.File;
import	java.io.FileInputStream;
import	java.io.FileOutputStream;
import	java.io.BufferedReader;
import	java.io.FileReader;
import	java.io.FileWriter;
import	java.io.PrintWriter;
import	java.text.SimpleDateFormat;
import	java.util.ArrayList;
import	java.util.Collections;
import	java.util.Date;
import	java.util.Iterator;
import	java.util.Vector;

/**	Generates a (cross-)reference listing of Java classes in different
 *	output formats: as HTML (&lt;dd&gt; list with links), Comma Separated Values, SQL
 *	(DROP/CREATE + INSERTs) or XML.
 *	Evaluates the file <em>dependencies.txt</em> written by Ant task
 *	<em>depend</em> with option <em>cache</em>.
 *	This file has the following structure:
 <pre>
||:gramword.MorphemTester
java.lang.System
java.util.regex.Pattern
java.sql.PreparedStatement
java.lang.Class
java.io.PrintStream
java.lang.Character
java.sql.Statement
org.punctum.common.DbAccess
org.apache.log4j.Logger
java.util.Iterator
java.lang.StringBuffer
gramword.PartList
java.lang.Exception
gramword.MorphemList
numword.DeuSpeller
java.lang.ClassNotFoundException
java.sql.ResultSet
gramword.Morphem
java.lang.NoClassDefFoundError
java.lang.Object
java.sql.Connection
java.lang.String
gramword.MorphemTester
java.util.regex.Matcher
||:gramword.GrammarFilter
java.lang.System
java.nio.channels.Channels
java.util.regex.Pattern
java.lang.Class
java.io.FileInputStream
java.text.DecimalFormat
...

 </pre>
 * @author Dr. Georg Fischer
 * @since 2006-08-15
 */
public class ClassXref extends Task {

	/*	Attributes of this task: 
		format
		sort
		srcfile
		destfile
		failonerror
	*/
    /** Output format: "html", "xml" or "sql" */
    private String format = "html";
    /** 
     *	Sets the <em>format</em> attribute.
     *	@param fmt output format: html, xml or sql
     */
    public void setFormat(String fmt) {
        format = fmt;
    } // setFormat

    /** Sort order of listing */
    String sort = "Dr";
    /** Sets the <em>sort</em> attribute.
     *	@param sort specification for the listing order
     *	by exactly two of the following letters "d" and "r" in either case:
	 *	</p><p><table border="1">
     *	<tr><td><strong>D</strong></td><td>calle"d" class or "d"efinition, with     package name</td></tr>	
     *	<tr><td><strong>d</strong></td><td>calle"d" class or "d"efinition, without  package name</td></tr>	
     *	<tr><td><strong>R</strong></td><td>calle"r" class or "r"eference,  with     package name</td></tr>	
     *	<tr><td><strong>r</strong></td><td>calle"r" class or "r"eference,  without  package name</td></tr>	
	 *	</table></p><p>
	 *	Default is "Dr" = order by definition with full names, and references with 
	 *	trailing name only. "RD" would give a list of full class names with all their imports.
     */
    public void setSort(String sort) {
        this.sort = sort;
    } // setSort

    /** Name of the input file */
    private java.io.File srcFile;
    
    /** Sets the <em>srcfile</em> attribute.
     *	@param src name of the input file containing the dependencies
     */
    public void setSrcfile(java.io.File src) {
        srcFile = src;
    } // setSrcFile

    /** Name of the output file */
    private java.io.File destFile;
    
    /** Sets the <em>destfile</em> attribute.
     *	@param dest name of the output file
     */
    public void setDestfile(java.io.File dest) {
        destFile = dest;
    } // setDestFile

    /** Should the build fail? Defaults to <i>false</i>. As attribute. */
    boolean failOnError = false;
    
    /** Sets the <em>failonerror</em> attribute.
     *	@param foe true if the build should fail on an error
     */
    public void setFailonerror(boolean foe) {
        failOnError = foe;
    } // setFailonerror

	/** Vector for nested <em>exclude</em> elements */
    private Vector<Exclude> excludes = new Vector<Exclude>();                          // 2
    
	/** Adds a new <em>exclude</em> element to the vector, 
	 *	called from <em>ant</em>
	 *	@return the element just created
	 */
    public Exclude createExclude() {                                 // 3
        Exclude excl = new Exclude();
        excludes.add(excl);
        return excl;
    } // createExclude
    
	/** Inner class for nested <em>exclude</em> elements */
    public class Exclude {                                           // 1
        public Exclude() {}
        String pattern;
        public void setPattern(String patt) { 
        	pattern = patt; 
        }
        public String getPattern() { 
        	return pattern; 
        }
    } // class Exclude
    
    /** Decides whether the parameter class can be excluded
     *	from the (cross-) reference list
     *	@param callerdClass a fully qualified classname
     *	@return true if the class can be excluded
     */
    private boolean isExcluded(String callerdClass) {
    	boolean result = false;
    	boolean busy = excludes.size() > 0;
    	if (busy) {
    		Iterator iter = excludes.iterator();
    		while (busy && iter.hasNext()) {
    			String patt = ((Exclude) iter.next()).getPattern().replace("*",""); 
    				// remove optional trailing "*"
    			if (callerdClass.startsWith(patt)) {
    				busy = false;
    				result = true;
    			}
    		} // while
    	} 
    	return result;
    } // isExcluded

    /** Do the work. */
    public void execute() {
        if (failOnError) {
        	throw new BuildException("Fail requested.");
        }
		processArgs(new String[] {"-s"});
    } // execute
    
    //--------------------------------------------------------
	public final static String CVSID = "@(#) $Id: ClassXref.java 387 2010-03-29 06:38:33Z  $";

	/** Newline string (CR+LF for Windows, LF for Unix) */
	private String nl;

	/** Level of test output (0 = none, 1 = some, 2 = more ...) */
	private int debug = 0;

	/** Non-empty indicator for empty package */
	private static final String EMPTY = "<empty>";

	/** Internal separator for fields in <em>classref</em> list */
	private static final String SEPARATOR = "#";

	/** Array where to sort both components of classnames: the package
	 *	name and the trailing classname.
	 *	The elements of this array are quadruples of strings, 
	 *	separated by the <em>SEPARATOR</em>.
	 *	Depending on attribute <em>sort</em>, these 4 strings are:
	 *	</p><p><table border="1">
	 *	<tr><td><tt>DR</tt></td><td><tt>calledPack#calledName#callerPack#callerName</tt></td></tr>
	 *	<tr><td><tt>Dr</tt></td><td><tt>calledPack#calledName#callerName#callerPack</tt></td></tr>
	 *	<tr><td><tt>dR</tt></td><td><tt>calledName#calledPack#callerPack#callerName</tt></td></tr>
	 *	<tr><td><tt>dr</tt></td><td><tt>calledName#calledPack#callerName#callerPack</tt></td></tr>
	 *	<tr><td><tt>RD</tt></td><td><tt>callerPack#callerName#calledPack#calledName</tt></td></tr>
	 *	<tr><td><tt>rD</tt></td><td><tt>callerPack#callerName#calledName#calledPack</tt></td></tr>
	 *	<tr><td><tt>Rd</tt></td><td><tt>callerName#callerPack#calledPack#calledName</tt></td></tr>
	 *	<tr><td><tt>rd</tt></td><td><tt>callerName#callerPack#calledName#calledPack</tt></td></tr>
	 *	</table></p><p>
	 *	For empty packages a fictitious package name "&lt;empty&gt;"
	 *	is inserted (and removed after sorting).
	 */
	private ArrayList/*<1.5*/<String>/*1.5>*/ classref = new ArrayList/*<1.5*/<String>/*1.5>*/(1024);
	
	/** Class which references other classes, 
	 *	maintained over several calls of <em>processLine</em> 
	 */
	private String callingClass = null;

	/** Derived from <em>sort</em>: whether to produce a crossreference 
	 *	(ordered by called  classes), otherwise ordered by caller classes
	 */
	private boolean xref;
	
	/** Derived from <em>sort</em>: whether the 1st letter is lowercase
	 */
	private boolean lower0;
	
	/** Derived from <em>sort</em>: whether the 2nd letter is lowercase
	 */
	private boolean lower1;
	
	/**	Constructor
	 */
	public ClassXref() {
		nl = System.getProperty("line.separator");
	}

	/**	Writes the header of the output file
	 *	@param writer writer to be used for output
	 *	@param title text to be written over the main column
	 *	@param mode output mode: html, xml, sql
	 */
	private void putHeader(PrintWriter writer, String mode, String title) { 
		try {
			String timeStampText 
					=  "generated by org.apache.tools.ant.taskdefs.ClassXref at "
					+ (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
							.format(new java.util.Date()) // ss.SSS?
					+ " with sort=\"" + sort + "\""
					;
			if (false) {
			} else if (mode.equals("csv")) {
    			writer.println("calledPack,calledName,callerPack,callerName");
			} else if (mode.equals("html")) {
				writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"");
    			writer.println("\t\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"");
				writer.println("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
				writer.println("<head>");
				writer.println("<!-- automatically generated by ClassXref.java - do not edit here! -->");
		    	writer.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />");
				writer.println("<title>" + title + "</title>");
				writer.println("<link rel=\"stylesheet\" title=\"all\" type=\"text/css\""
								+ " href=\"stylesheet.css\" />");
				writer.println("</head>");
				writer.println("<body>");
				writer.println("<h2>" + title + "</h2>");
				writer.println("<h4>" + timeStampText + "</h2>");
				writer.println("<dl>");
			} else if (mode.equals("sql")) {
    			writer.println("-- " + timeStampText);
				writer.println("DROP   TABLE classxref IF EXISTS;");
    			writer.println("CREATE TABLE classxref");
				writer.println("  ( calledpack varchar(256)");
				writer.println("  , calledname varchar(256)");
				writer.println("  , calledname varchar(256)");
				writer.println("  , calledname varchar(256)");
				writer.println("  );");
			} else if (mode.equals("xml")) {
				writer.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
				writer.println("<!-- " + title + " -->");
				writer.println("<classxref>");
				writer.println("\t<description>" + timeStampText + "</description>");
			} else {
				System.err.println("invalid mode \"" + mode + "\" - abort");
				// System.exit(1);
			}
		} catch (Exception exc) {
			System.err.println(exc.getMessage());
			exc.printStackTrace();
		}
	} // putHeader
	
	/**	Writes the trailer of the output file
	 *	@param writer writer to be used for output
	 *	@param mode output mode: html, xml, sql, csv
	 */
	private void putTrailer(PrintWriter writer, String mode) {
		try {
			if (false) {
			} else if (mode.equals("csv")) {
			} else if (mode.equals("html")) {
				writer.println("</dd></dl>");
				writer.println("</body>");
				writer.println("</html>");
			} else if (mode.equals("sql")) {
				writer.println("COMMIT;");
			} else if (mode.equals("xml")) {
				writer.println("\t</called>");
				writer.println("</classxref>");
			} else {
				// System.err.println("invalid mode \"" + mode + "\" - abort");
				// System.exit(1);
			}
		} catch (Exception exc) {
			System.err.println(exc.getMessage());
			exc.printStackTrace();
		}
	} // putTrailer
	
	/**	Evaluates a line of the input (dependencies) file
	 *	@param line string containing calling or called class
	 */
	private void processLine(String line, String strategy) {
		try {
			if (line.startsWith("[")) { 
				// strange, error in <depend> task? - ignore line
			} else if (line.startsWith("||:")) { 
				// ant.depend.cache(dependencies.txt) uses this convention
				// 'callingClass' is global because it's valid 
				// over several calls of 'processLine'
				callingClass = line.substring(3);
			} else { 
				String calledClass = line;
				if (! isExcluded(calledClass) && ! isExcluded(callingClass)) {
					if (xref) {
						classref.add( separate(lower0, calledClass ) 
									+ SEPARATOR 
									+ separate(lower1, callingClass)
									);
					} else {
						classref.add( separate(lower0, callingClass)
									+ SEPARATOR 
									+ separate(lower1, calledClass ) 
									);
					}
				} // ! excluded
			}
		} catch (Exception exc) {
			System.err.println(exc.getMessage());
			exc.printStackTrace();
		}
	} // processLine
	
	/**	Separates the package name (if any) from the constructor class name.
	 *	@param lower false for order by full class name, 
	 *	true for order by trainling class name only
	 *	@param fullName name of the class, e.g. </em>org.punctum.common.ClassXref</em>
	 *	@return separated names, e.g. </em>org.punctum.common ClassXref</em>
	 */
	private String separate(boolean lower, String fullName) {
		int lastDotPos = fullName.lastIndexOf('.');
		String result;
		if (lower) { // trailing
			result = (lastDotPos == -1) 
				? 	fullName + SEPARATOR + EMPTY
				: 	fullName.substring(lastDotPos + 1)
					+ SEPARATOR
					+ fullName.substring(0, lastDotPos) 
				;
		} else { // full
			result = (lastDotPos == -1) 
				? 	EMPTY + SEPARATOR + fullName
				: 	fullName.substring(0, lastDotPos) 
					+ SEPARATOR
					+ fullName.substring(lastDotPos + 1)
				;
		}
		return result;
	} // separate
	
	/**	Sorts the array, and writes it to the output stream.
	 *	@param writer writer to be used for output
	 *	@param mode output mode: csv, html, sql, xml
	 */
	private void outputSortedArray(PrintWriter writer, String mode) {
		try {
			String calledClass = null;
			Collections.sort(classref);
			Iterator iter = classref.iterator();
			while (iter.hasNext()) {
				String line = ((String) iter.next());
				String [] parts = (line).split(SEPARATOR);
				// System.err.println(line + "* " + parts.length);
				int ip = 0;
				// CAUTION, the names in the following code are meant for 'xref = true';
				// the HTML code works equally well for 'xref = false' (caller, called); 
				// we eventually switch the pairs for CSV, SQL and XML ('called' always first)
				String calledName;
				String calledPack;
				String callerName;
				String callerPack;
				if (lower0) {
					calledName = parts[ip ++];
					calledPack = parts[ip ++];
				} else {
					calledPack = parts[ip ++];
					calledName = parts[ip ++];
				}
				if (lower1) {
					callerName = parts[ip ++];
					callerPack = parts[ip ++];
				} else {
					callerPack = parts[ip ++];
					callerName = parts[ip ++];
				}
				if (! (calledPack + calledName).equals(calledClass)) { 
					// start of new 'calledClass'
					if (false) {
					} else if (mode.equals("csv")) {
						// output in 1 line below
					} else if (mode.equals("html")) {
						if (calledClass != null) {
							writer.println("</dd>");
						}
						writer.println("<dt><a name=\"" 
							+ 	(calledPack.equals(EMPTY) ? "" : calledPack + ".")
							+ 	calledName + "\" />" 
							+ 	(	lower0
								? 	// trailing classname only
									( 	"<strong>" + calledName + "</strong>"
									+	(calledPack.equals(EMPTY)
										?	" in no package"
										:	(" in package " + calledPack)
										)
									)
								: 	// full classname
									( 	(calledPack.equals(EMPTY) ?	"" : (calledPack + "."))
										+	"<strong>" + calledName + "</strong>"	
									)
								)
								// + (xref ? "is used by" : "uses")
							+ "</dt><dd>");
					} else if (mode.equals("sql")) {
						// output in 1 line below
					} else if (mode.equals("xml")) {
						if (calledClass != null) {
							writer.println("\t</called>");
						}
						writer.println("\t<called pack=\"" 
								+ (xref ? calledPack : callerPack).replaceAll("<empty>", "")
								+ "\" name=\"" + (xref ? calledName : callerName) + "\">");
					}
					calledClass = calledPack + calledName;
				} // if new 'calledClass'
				
				if ((callerPack + callerName).equals(calledClass)) {
					// ignore self reference
				} else if (mode.equals("csv")) {
						writer.println(	(xref ? calledPack : callerPack).replaceAll("<empty>", "")
							+ "," 	+	(xref ? calledName : callerName)
							+ ","	+	(xref ? callerPack : calledPack).replaceAll("<empty>", "")
							+ ","	+	(xref ? callerName : calledName)
							);
				} else if (mode.equals("html")) {
					writer.print(	(xref ? " &lt; " : " &gt; ")
							+ 	"<a href=\"#" 
							+ 	(callerPack.equals(EMPTY) ? "" : callerPack + ".")
							+ 	callerName + "\">"
							+ 	(	lower1
								? 	// trailing classname only
									( 	"<strong>" + callerName + "</strong>"
									)
								: 	// full classname
									( 	(callerPack.equals(EMPTY) ?	"" : (callerPack + "."))
										+	"<strong>" + callerName + "</strong>"
									)								
								)
								// + (xref ? "is used by" : "uses")
							+ 	"</a>");
				} else if (mode.equals("sql")) {
					writer.println("INSERT into classxref VALUES"
							+  " (\'" + (xref ? calledPack : callerPack).replaceAll("<empty>", "")
							+ "\',\'" + (xref ? calledName : callerName)
							+ "\',\'" + (xref ? callerPack : calledPack).replaceAll("<empty>", "")
							+ "\',\'" + (xref ? callerName : calledName)
							+ "\');");
				} else if (mode.equals("xml")) {
					writer.println("\t\t<caller pack=\"" 
							+ (xref ? callerPack : calledPack ).replaceAll("<empty>", "")
							+ "\" name=\"" 
							+ (xref ? callerName : calledName ) 
							+ "\" />");
				}
			} // while iter
		} catch (Exception exc) {
			System.err.println(exc.getMessage());
			exc.printStackTrace();
		}
	} // outputSortedArray

	/**	Evaluates the arguments of the command line, and processes them.
	 *	@param args Arguments; if missing, print the following usage
	 *	<pre>
	 *	usage:  java org.apache.tools.ant.taskdefs.ClassXref [-f format] [-s [DR|Dr|dr|RD|Rd|rd]] [-x java.,javax.] infile outfile
	 *	-f		csv | html (default) | sql | xml : output format
	 *	-s		DR | Dr | dR | dr | RD | Rd | rD| rd : sort order
	 *	-x		list of exclude patterns for standard classes
	 *	</pre>
	 */
	private void processArgs(String args[]) {
		try {
			int iarg = 0; // index for command line arguments
			StringBuffer title = new StringBuffer(128);
			while (iarg < args.length) {
				title.append(args[iarg ++]);
				title.append(" ");
			}
			iarg = 0;
			if (iarg >= args.length) { // usage, with known ISO codes and languages
				System.err.println("usage: java org.apache.tools.ant.taskdefs.ClassXref "
						+ " [-f format] [-s sort] infile outfile");
				System.err.println("  -s  DR | Dr | dR | dr | RD | Rd | rD | rd : sort order");
				System.err.println("  -f  csv | html(default) | sql | xml  : output format");
	 			System.err.println("  -x  list of exclude patterns for standard classes");
			} else { // >= 1 argument 
				String 	strategy = "."; // default: show all classes (no class name starts with ".")
				
				// get all options
				while (iarg < args.length && args[iarg].startsWith("-")) {
					String option = args[iarg ++].substring(1);
					if (false) {
					} else if (option.startsWith("s")) {
						if (iarg < args.length) {
							sort = args[iarg ++];
						}
					} else if (option.startsWith("f")) {
						if (iarg < args.length) {
							format = args[iarg ++];
						}
					} else if (option.startsWith("x")) {
						if (iarg < args.length) {
							String [] patts = args[iarg ++].split(",");
							for (int index = 0; index < patts.length; index ++) {
								Exclude excl = createExclude();
								excl.setPattern(patts[index]);
							}
						}
					}
				} // while options
				
				if (iarg < args.length) { // with another (filename) argument
					srcFile = new File(args[iarg ++]);
					if (iarg < args.length) { // with another (filename) argument
						destFile = new File(args[iarg ++]);
					}
				}
				if (true) {
					BufferedReader reader = new BufferedReader(new FileReader(srcFile));
					PrintWriter    writer = new PrintWriter   (new FileWriter(destFile));
					String line = null; 
					xref 	= sort.toLowerCase().substring(0, 1).equals("d");
					lower0	= sort.substring(0, 1).equals(sort.substring(0, 1).toLowerCase());
					lower1	= sort.substring(1, 2).equals(sort.substring(1, 2).toLowerCase());

					putHeader(writer, format, "Class " 
							+ (xref ? "Crossreference" : "Reference"));
					while ((line = reader.readLine()) != null) {
						processLine(line, strategy);
					} // while busy	
					outputSortedArray(writer, format);
					putTrailer(writer, format);

					writer.close();
					reader.close();
				} // with filename
				else {
				}
			} // args.length >= 1
		} catch (Exception exc) {
			System.err.println(exc.getMessage());
			exc.printStackTrace();
		}
		// return out.toString();
	} // processArgs
	
	/**	Main Program
	 *	@param args array of strings with options and files,
	 *	usage is shown if empty
	 */
	public static void main(String args[]) {
		ClassXref classXref = new ClassXref();
		classXref.setFormat("html");
		classXref.setSort  ("Dr");
		classXref.processArgs(args);
	} // main

} // ClassXref
