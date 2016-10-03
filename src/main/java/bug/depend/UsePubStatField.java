package bug.depend;
import  bug.PubStatField;
/**
 *	A simple class which uses the constant in <em>Status</em>
 *	@author Dr. Georg Fischer
 */
public class UsePubStatField { 
	/**
	 *	Main Test Program
	 */
	public static void main(String [] args) {
		System.out.println("Field value = " + PubStatField.FIELD1);
	}
}
