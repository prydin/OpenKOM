/*
 * Created on Aug 25, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.UnstoredMessage;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin </a>
 */
public class FileEditor extends AbstractEditor
{

    public FileEditor(Context context) throws IOException,
            UnexpectedException
    {
        super("fileeditorcommands.xml", context);
    }

	protected void refresh() throws KOMException
	{
	    new ShowSimpleFile(m_context, "", 0).execute(m_context, new Object[0]);
	}
    
	//FIXME EDITREFACTOR: replyTo is completely irrelevant in this context.
    public UnstoredMessage edit()
            throws KOMException, InterruptedException, IOException
    {
        this.mainloop(false);
        return new UnstoredMessage("", m_context.getBuffer().toString());
    }
    
    protected String getAbortQuestionFormat()
    {
        return "simple.editor.abortfilequestion";
    }
}