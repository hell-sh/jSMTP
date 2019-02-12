package sh.hell.jsmtp.content;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public enum SMTPEncoding
{
	SEVENBIT("7bit"),
	QUOTED_PRINTABLE("quoted-printable"),
	BASE64("base64"),
	EIGHTBIT("8bit");

	public final String name;

	SMTPEncoding(String name)
	{
		this.name = name;
	}

	public static SMTPEncoding fromName(String name)
	{
		for(SMTPEncoding encoding : SMTPEncoding.values())
		{
			if(name.equalsIgnoreCase(encoding.name))
			{
				return encoding;
			}
		}
		return null;
	}

	public String encode(String text)
	{
		if(this == QUOTED_PRINTABLE)
		{
			StringBuilder str = new StringBuilder();
			for(char c : text.toCharArray())
			{
				if((c < 33 || c > 126 || c == 61) && c != 13 && c != 10)
				{
					str.append("=").append(String.format("%2X", (byte) c).replace(" ", "0"));
				}
				else
				{
					str.append(c);
				}
			}
			return str.toString();
		}
		else if(this == BASE64)
		{
			return new String(Base64.getEncoder().encode(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		}
		return text;
	}

	public String decode(String text)
	{
		if(this == QUOTED_PRINTABLE)
		{
			StringBuilder str = new StringBuilder();
			int decoding = 0;
			StringBuilder decodeChars = new StringBuilder();
			for(char c : text.toCharArray())
			{
				if(decoding > 0)
				{
					if(c > 32 && c < 127 && c != 61)
					{
						decodeChars.append(c);
						if(decoding != 2)
						{
							decoding++;
							continue;
						}
						str.append((char) Integer.parseInt(decodeChars.toString(), 16));
					}
					decoding = 0;
					decodeChars = new StringBuilder();
				}
				else
				{
					if(c == 61) // =
					{
						decoding = 1;
					}
					else
					{
						str.append(c);
					}
				}
			}
			return str.toString();
		}
		else if(this == BASE64)
		{
			return new String(Base64.getDecoder().decode(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		}
		return text;
	}
}
