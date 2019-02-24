package sh.hell.jsmtp.content;

import sh.hell.jsmtp.exceptions.InvalidHeaderException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SMTPMail
{
	public final ArrayList<SMTPAddress> recipients = new ArrayList<>();
	public final HashMap<String, String> headers = new HashMap<>();
	public SMTPAddress sender;
	public SMTPContent contents;

	public SMTPMail()
	{
		headers.put("date", SMTPContent.RFC2822.format(new Date()));
		headers.put("mime-version", "1.0");
	}

	public SMTPMail from(SMTPAddress sender)
	{
		if(!sender.isValid())
		{
			sender = sender.validCopy();
		}
		this.sender = sender;
		try
		{
			addHeader("from", sender.toString());
		}
		catch(InvalidHeaderException e)
		{
			e.printStackTrace();
		}
		return this;
	}

	public SMTPMail to(SMTPAddress recipient)
	{
		if(!recipient.isValid())
		{
			recipient = recipient.validCopy();
		}
		synchronized(recipients)
		{
			recipients.add(recipient);
		}
		synchronized(headers)
		{
			if(headers.containsKey("to"))
			{
				try
				{
					addHeader("cc", recipient.toString());
				}
				catch(InvalidHeaderException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				headers.put("to", recipient.toString());
			}
		}
		return this;
	}

	public SMTPMail cc(SMTPAddress smtpAddress)
	{
		return this.to(smtpAddress);
	}

	public SMTPMail bcc(SMTPAddress recipient)
	{
		if(!recipient.isValid())
		{
			recipient = recipient.validCopy();
		}
		synchronized(recipients)
		{
			recipients.add(recipient);
		}
		return this;
	}

	/**
	 * Sets the subject header or appends to it if already present.
	 *
	 * @param subject The subject of the email.
	 * @return this
	 */
	public SMTPMail subject(String subject)
	{
		try
		{
			return this.addHeader("subject", subject);
		}
		catch(InvalidHeaderException e)
		{
			e.printStackTrace();
		}
		return this;
	}

	/**
	 * Creates a header or appends to its value if it already exists.
	 *
	 * @param name  The name of the header.
	 * @param value The value of the header.
	 * @return this
	 * @throws InvalidHeaderException If the header is invalid.
	 */
	public SMTPMail addHeader(String name, String value) throws InvalidHeaderException
	{
		if(name.contains(":"))
		{
			throw new InvalidHeaderException("Invalid header name: " + name);
		}
		name = name.toLowerCase();
		synchronized(headers)
		{
			if(headers.containsKey(name))
			{
				headers.put(name, headers.get(name) + ", " + value);
			}
			else
			{
				headers.put(name, value);
			}
		}
		return this;
	}

	public SMTPMail removeHeader(String name)
	{
		synchronized(headers)
		{
			headers.remove(name);
		}
		return this;
	}

	/**
	 * Sets the contents of the email.
	 *
	 * @param contents the contents of the email.
	 * @return this
	 */
	public SMTPMail data(SMTPContent contents)
	{
		this.contents = contents;
		return this;
	}

	public String getRawContents()
	{
		StringBuilder contents = new StringBuilder();
		synchronized(headers)
		{
			for(Map.Entry<String, String> header : headers.entrySet())
			{
				contents.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
			}
		}
		for(String line : this.contents.getBody().split("\r\n"))
		{
			if(line.startsWith("."))
			{
				contents.append(".").append(line).append("\r\n");
			}
			else
			{
				contents.append(line).append("\r\n");
			}
		}
		return contents.toString();
	}

	@Override
	public String toString()
	{
		StringBuilder str = new StringBuilder("SMTPMail from ").append(sender == null ? "nobody" : sender.toString()).append(" to ");
		if(recipients.size() == 0)
		{
			str.append("nobody");
		}
		else
		{
			str.append(recipients.get(0).toString());
			for(int i = 1; i < recipients.size(); i++)
			{
				str.append(", ").append(recipients.get(i).toString());
			}
		}
		str.append(":\n");
		for(Entry<String, String> entry : headers.entrySet())
		{
			str.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}
		return str.append("\n").append(contents.toString()).toString();
	}
}
