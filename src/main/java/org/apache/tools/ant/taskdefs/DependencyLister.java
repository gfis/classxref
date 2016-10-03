package org.apache.tools.ant.taskdefs;
import java.io.*;
import java.util.*;
import  org.apache.bcel.classfile.*;
import  org.apache.bcel.*;

/**
 * Read class file(s) and display its contents. The command line usage is:
 *
 * <pre>java DependencyLister [-constants] [-code] [-brief] [-dependencies] [-nocontents] [-recurse] class... [-exclude <list>]</pre>
 * where
 * <ul>
 * <li><tt>-code</tt> List byte code of methods</li>
 * <li><tt>-brief</tt> List byte codes briefly</li>
 * <li><tt>-constants</tt> Print constants table (constant pool)</li>
 * <li><tt>-recurse</tt>  Usually intended to be used along with
 * <tt>-dependencies</tt>  When this flag is set, DependencyLister will also print information
 * about all classes which the target class depends on.</li>
 * 
 * <li><tt>-dependencies</tt>  Setting this flag makes DependencyLister print a list of all
 * classes which the target class depends on.  Generated from getting all
 * CONSTANT_Class constants from the constant pool.</li>
 * 
 * <li><tt>-exclude</tt>  All non-flag arguments after this flag are added to an
 * 'exclusion list'.  Target classes are compared with the members of the
 * exclusion list.  Any target class whose fully qualified name begins with a
 * name in the exclusion list will not be analyzed/listed.  This is meant
 * primarily when using both <tt>-recurse</tt> to exclude java, javax, and sun classes,
 * and is recommended as otherwise the output from <tt>-recurse</tt> gets quite long and
 * most of it is not interesting.  Note that <tt>-exclude</tt> prevents listing of
 * classes, it does not prevent class names from being printed in the
 * <tt>-dependencies</tt> list.</li>
 * <li><tt>-nocontents</tt> Do not print JavaClass.toString() for the class. I added
 * this because sometimes I'm only interested in dependency information.</li>
 * </ul>
 * <p>Here's a couple examples of how I typically use DependencyLister:<br>
 * <pre>java DependencyLister -code MyClass</pre>
 * Print information about the class and the byte code of the methods
 * <pre>java DependencyLister -nocontents -dependencies MyClass</pre>
 * Print a list of all classes which MyClass depends on.
 * <pre>java DependencyLister -nocontents -recurse MyClass -exclude java. javax. sun.</pre>
 * Print a recursive listing of all classes which MyClass depends on.  Do not
 * analyze classes beginning with "java.", "javax.", or "sun.".
 * <pre>java DependencyLister -nocontents -dependencies -recurse MyClass -exclude java.javax. sun.</pre>
 * Print a recursive listing of dependency information for MyClass and its
 * dependents.  Do not analyze classes beginning with "java.", "javax.", or "sun."
 * </p>
 *
 * @version $Id: DependencyLister.java 387 2010-03-29 06:38:33Z  $
 * @author  <A HREF="http://www.berlin.de/~markus.dahm">M. Dahm</A>,
 * <a href="mailto:twheeler@objectspace.com">Thomas Wheeler</A>
 */
public class DependencyLister {
    boolean   code, constants, verbose, classdep, nocontents, recurse;
    Vector    excludeName;

    /**
     *  Constructor
     */
    public DependencyLister(boolean code, boolean constants, boolean verbose, boolean classdep,
             boolean nocontents, boolean recurse, Vector excludeName) {
        this.code = code;
        this.constants = constants;
        this.verbose = verbose;
        this.classdep = classdep;
        this.nocontents = nocontents;
        this.recurse = recurse;
        this.excludeName = excludeName;
    }

    /** 
     *  Print the given class on screen
     */
    public void list(String name) {
        try {
            JavaClass clazz;

            if (name.startsWith("[")) {
                return;
            }

            for (int idx = 0; idx < excludeName.size(); idx++) {
                if (name.startsWith((String) excludeName.elementAt(idx)))
                    return;
            }

            if ((clazz = Repository.lookupClass(name)) == null) {
                clazz = new ClassParser(name).parse(); // May throw IOException
            }
            printClassDependencies(name, clazz.getConstantPool());
        } catch (IOException exc) {
            System.err.println("Error loading class " + name + " (" + exc.getMessage() + ")");
            exc.printStackTrace();
        }
        catch (Exception exc) {
            System.err.println("Error processing class " + name + " (" + exc.getMessage() + ")");
            exc.printStackTrace();
        }
    }

    /**
     * Dump the list of classes this class is dependent on
     */
    public static void printClassDependencies(String name, ConstantPool pool) {
        StringBuffer buf = new StringBuffer();
        for (int idx = 0; idx < pool.getLength(); idx++) {
            Constant c = pool.getConstant(idx);
            if (c != null && c.getTag() == Constants.CONSTANT_Class) {
                ConstantUtf8 c1 = (ConstantUtf8) pool.getConstant(((ConstantClass) c).getNameIndex());
                buf.setLength(0);
                buf.append(new String(c1.getBytes()));
            /*
                for (int n = 0; n < buf.length(); n++) {
                    if (buf.charAt(n) == '/')
                        buf.setCharAt(n, '.');
                }
            */
                String depend = buf.toString().replaceAll("/",".");
                if (! name.equals(depend)) {
                    System.out.println(name + " " + depend);
                }
            }
        }
    }

    public static void main(String[] argv) {
        Vector/*<1.5*/<String>/*1.5>*/ fileName    = new Vector/*<1.5*/<String>/*1.5>*/();
        Vector/*<1.5*/<String>/*1.5>*/ excludeName = new Vector/*<1.5*/<String>/*1.5>*/();
        boolean code=false, constants=false, verbose=true, classdep=false,
                nocontents=false, recurse=false, exclude=false;
        String  name         = null;

        /* Parse command line arguments.
         */
        for (int i=0; i < argv.length; i++) {
            if (argv[i].charAt(0) == '-') {  // command line switch
                if (false) {
                }
                else if (argv[i].equals("-exclude"))
                    exclude=true;
                else if (argv[i].equals("-help")) {
                    System.out.println( "Usage: java DependencyLister " +
                        " class... " +
                        "[-exclude <list>]\n" +
                        "-exclude <list>  Do not list classes beginning with " +
                        "                 strings in <list>" );
                    System.exit( 0 );
                } else
                    System.err.println("Unknown switch " + argv[i] + " ignored.");
            } else { // add file name to list
                if (exclude)
                    excludeName.addElement(argv[i]);
                else
                    fileName.addElement(argv[i]);
            }
        }

        if (fileName.size() == 0)
            System.err.println("list: No input files specified");
        else {
            DependencyLister lister = new DependencyLister(code, constants, verbose, classdep,
                        nocontents, recurse, excludeName);

            for (int i=0; i < fileName.size(); i++) {
                name = (String) fileName.elementAt(i);
                lister.list(name);
            }
        }
    }

}
