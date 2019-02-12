package sh.hell.jsmtp.client;

import java.util.ArrayList;

public class SMTPResponse
{
	final String status;
	final ArrayList<String> lines = new ArrayList<>();

	SMTPResponse(String line)
	{
		this.status = line.substring(0, 3);
		this.lines.add(line.substring(4));
	}

	@Override
	public String toString()
	{
		StringBuilder string = new StringBuilder(this.status).append(" ").append(lines.get(0));
		for(int i = 1; i < lines.size(); i++)
		{
			string.append("\n").append(lines.get(i));
		}
		return string.toString();
	}
}
