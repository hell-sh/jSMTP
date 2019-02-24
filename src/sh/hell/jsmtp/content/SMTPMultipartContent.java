package sh.hell.jsmtp.content;

import java.util.ArrayList;
import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class SMTPMultipartContent extends SMTPContent
{
	public final ArrayList<SMTPContent> parts = new ArrayList<>();
	String boundary;

	public SMTPMultipartContent()
	{
		super("multipart/alternative");
		this.boundary = UUID.randomUUID().toString().replace("-", "");
	}

	public SMTPMultipartContent addPart(SMTPContent part)
	{
		synchronized(parts)
		{
			if(part instanceof SMTPMultipartContent)
			{
				while(((SMTPMultipartContent) part).boundary.equals(this.boundary))
				{
					this.boundary = UUID.randomUUID().toString().replace("-", "");
				}
			}
			parts.add(part);
		}
		return this;
	}

	@Override
	public String getBody()
	{
		synchronized(parts)
		{
			final StringBuilder body = new StringBuilder();
			body.append("content-type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\ncontent-transfer-encoding: 7bit\r\n\r\n");
			for(SMTPContent part : parts)
			{
				body.append("--").append(boundary).append("\r\n").append(part.getBody()).append("\r\n");
			}
			return body.append("--").append(boundary).append("--").toString();
		}
	}

	@Override
	public String toString()
	{
		StringBuilder str = new StringBuilder("---" + type + "---\n");
		synchronized(parts)
		{
			for(SMTPContent part : parts)
			{
				str.append(part.toString()).append("\n");
			}
		}
		return str.append("---").toString();
	}
}
