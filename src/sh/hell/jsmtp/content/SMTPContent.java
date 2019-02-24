package sh.hell.jsmtp.content;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.Scanner;

@SuppressWarnings("WeakerAccess")
public abstract class SMTPContent
{
	public static final SimpleDateFormat RFC2822 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
	public final String type;

	public SMTPContent(String type)
	{
		this.type = type;
	}

	public SMTPContent()
	{
		this(null);
	}

	private static boolean readPart(Scanner scanner, String boundary, HashMap<String, String> headers, StringBuilder partBody)
	{
		boolean isEnd = false;
		boolean headersDefined = false;
		String lastHeader = null;
		do
		{
			String line = scanner.next();
			if(line.equals(boundary))
			{
				break;
			}
			if(line.equals(boundary + "--"))
			{
				isEnd = true;
				break;
			}
			if(!headersDefined)
			{
				if(line.contains(":"))
				{
					String[] arr = line.split(":");
					if(arr.length > 1)
					{
						lastHeader = arr[0].toLowerCase();
						headers.put(lastHeader, line.substring(arr[0].length() + 1).trim());
					}
					continue;
				}
				if(line.length() == 0)
				{
					headersDefined = true;
				}
				else if(lastHeader != null)
				{
					headers.put(lastHeader, headers.get(lastHeader) + line.trim());
				}
			}
			else
			{
				partBody.append(line).append("\n");
			}
		}
		while(scanner.hasNext());
		return isEnd;
	}

	/**
	 * Converts an email's headers & body into its SMTPContent representation.
	 *
	 * @param headers The headers of the email.
	 * @param body    The body of the email.
	 * @return The email's SMTPContent representation.
	 */
	public static SMTPContent from(HashMap<String, String> headers, String body)
	{
		SMTPEncoding encoding = SMTPEncoding.SEVENBIT;
		if(headers.containsKey("content-transfer-encoding"))
		{
			encoding = SMTPEncoding.fromName(headers.get("content-transfer-encoding"));
			if(encoding == null)
			{
				encoding = SMTPEncoding.SEVENBIT;
			}
		}
		headers.putIfAbsent("content-type", "text/plain");
		String contentType = headers.get("content-type").replace("; ", ";");
		if(contentType.startsWith("multipart/alternative;boundary="))
		{
			String boundary = contentType.substring(31);
			if(boundary.substring(0, 1).equals("\"") && boundary.substring(boundary.length() - 1).equals("\""))
			{
				boundary = boundary.substring(1, boundary.length() - 1);
			}
			final SMTPMultipartContent content = new SMTPMultipartContent();
			Scanner scanner = new Scanner(encoding.decode(body).replace("\r", "")).useDelimiter("\n");
			SMTPContent.readPart(scanner, "--" + boundary, new HashMap<>(), new StringBuilder());
			do
			{
				HashMap<String, String> partHeaders = new HashMap<>();
				StringBuilder partBody = new StringBuilder();
				boolean isEnd = SMTPContent.readPart(scanner, "--" + boundary, partHeaders, partBody);
				if(partBody.length() > 1)
				{
					content.addPart(SMTPContent.from(partHeaders, partBody.toString().substring(0, partBody.length() - 1)));
				}
				else
				{
					content.addPart(SMTPContent.from(partHeaders, partBody.toString()));
				}
				if(isEnd)
				{
					break;
				}
			}
			while(scanner.hasNext());
			scanner.close();
			return content;
		}
		else if(headers.containsKey("content-disposition") && headers.get("content-disposition").startsWith("attachment"))
		{
			String filename = null;
			String contentDisposition = headers.get("content-disposition").replace("; ", ";");
			if(contentDisposition.startsWith("attachment;filename="))
			{
				filename = contentDisposition.substring(20);
				if(filename.substring(0, 1).equals("\"") && filename.substring(filename.length() - 1).equals("\""))
				{
					filename = filename.substring(1, filename.length() - 1);
				}
			}
			return new SMTPAttachment(contentType, filename, (encoding == SMTPEncoding.BASE64 ? Base64.getDecoder().decode(body) : encoding.decode(body).getBytes()));
		}
		else
		{
			return new SMTPTextContent(contentType, encoding.decode(body));
		}
	}

	public abstract String getBody();

	@Override
	public abstract String toString();
}
