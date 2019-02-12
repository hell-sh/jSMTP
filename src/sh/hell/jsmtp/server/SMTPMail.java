package sh.hell.jsmtp.server;

import sh.hell.jsmtp.SMTPAddress;
import sh.hell.jsmtp.content.SMTPContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

@SuppressWarnings("WeakerAccess")
public class SMTPMail
{
	public final ArrayList<SMTPAddress> recipients = new ArrayList<>();
	public final HashMap<String, String> headers = new HashMap<>();
	final StringBuilder body = new StringBuilder();
	public SMTPAddress sender;
	public SMTPContent content;

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
		str.append("\n");
		if(content == null)
		{
			str.append(body);
		}
		else
		{
			str.append(content.toString());
		}
		return str.toString();
	}
}
