/*
 * Created on Oct 5, 2003
 * 
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.IOException;
import java.io.PrintWriter;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.CommandLinePart;
import nu.rydin.kom.frontend.text.parser.CommandNamePart;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public interface Command
{
	public void execute(Context context, Object[] parameters)
	throws KOMException, IOException, InterruptedException;
	
	public String getFullName();
	
	/**
	 * Prints characters preceeding command output, typically 
	 * a newline.
	 * @param out The stream to print on
	 */
	public void printPreamble(PrintWriter out);
	
	/**
	 * Prints characters succeeding command output, typically
	 * a newline.
	 * @param out The stream to print on
	 */
	public void printPostamble(PrintWriter out);

	/**
	 * @return
	 */
	public CommandLineParameter[] getSignature();
	
	public CommandNamePart[] getNameSignature();
	
	public CommandLinePart[] getFullSignature();

}
