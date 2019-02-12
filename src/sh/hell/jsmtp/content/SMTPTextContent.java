package sh.hell.jsmtp.content;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SMTPTextContent extends SMTPContent
{
	public final String body;

	public SMTPTextContent(String type, String body)
	{
		super(type.split(";")[0]);
		this.body = body.replace("\r\n", "\n");
	}

	@Override
	public String getBody()
	{
		return "content-type: " + type + "; charset=\"UTF-8\"\ncontent-transfer-encoding: quoted-printable\n\n" + SMTPEncoding.QUOTED_PRINTABLE.encode(body);
	}

	@Override
	public String toString()
	{
		return "---" + type + "---\n" + body + "\n---";
	}
}
