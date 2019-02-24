package sh.hell.jsmtp.content;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
			for(byte b : text.getBytes(StandardCharsets.UTF_8))
			{
				if((b < 33 || b > 126 || b == 61) && b != 9 && b != 32)
				{
					str.append("=").append(String.format("%2X", b).replace(" ", "0"));
				}
				else
				{
					str.append((char) b);
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
			int decoding = 0;
			final ArrayList<Byte> content = new ArrayList<>();
			StringBuilder decodeChars = new StringBuilder();
			for(char c : text.toCharArray())
			{
				if(decoding > 0)
				{
					decodeChars.append(c);
					if(decoding++ >= 2)
					{
						content.add((byte) Integer.parseInt(decodeChars.toString(), 16));
						decoding = 0;
						decodeChars = new StringBuilder();
					}
				}
				else
				{
					if(c == '=')
					{
						decoding = 1;
					}
					else
					{
						content.add((byte) c);
					}
				}
			}
			byte[] bytearr = new byte[content.size()];
			for(int i = 0; i < content.size(); i++)
			{
				bytearr[i] = content.get(i);
			}
			return new String(bytearr, StandardCharsets.UTF_8);
		}
		else if(this == BASE64)
		{
			return new String(Base64.getDecoder().decode(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		}
		return text;
	}
}
