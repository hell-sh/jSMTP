package sh.hell.jsmtp.content;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SMTPAttachment extends SMTPContent
{
	public final String filename;
	public final byte[] bytes;

	public SMTPAttachment(String type, byte[] bytes)
	{
		this(type, null, bytes);
	}

	public SMTPAttachment(String type, String filename, byte[] bytes)
	{
		super(type);
		if(filename == null)
		{
			this.filename = null;
		}
		else
		{
			this.filename = filename.replace(" ", "_");
		}
		this.bytes = bytes;
	}

	public static SMTPAttachment fromFile(File file) throws IOException
	{
		return fromFile(file, Files.probeContentType(file.toPath()));
	}

	public static SMTPAttachment fromFile(File file, String contentType) throws IOException
	{
		return new SMTPAttachment(contentType, file.getName(), Files.readAllBytes(file.toPath()));
	}

	@Override
	public String getBody()
	{
		return "content-type: " + type + "\ncontent-disposition: attachment" + (filename == null ? "" : "; filename=" + filename) + " \ncontent-transfer-encoding: base64\n\n" + Base64.getEncoder().encodeToString(bytes);
	}

	@Override
	public String toString()
	{
		String str = "---" + type + "---\n";
		if(filename != null)
		{
			str += "filename: " + filename + "\n";
		}
		return str + "{" + bytes.length + " bytes}\n---";
	}
}
