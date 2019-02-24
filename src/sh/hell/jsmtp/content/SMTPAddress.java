package sh.hell.jsmtp.content;

import sh.hell.jsmtp.exceptions.InvalidAddressException;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Arrays;

@SuppressWarnings({"unused", "WeakerAccess"})
public class SMTPAddress
{
	/**
	 * The name of the entity that owns this address.
	 */
	public final String name;
	/**
	 * The email address.
	 */
	public final String mail;

	public SMTPAddress(String mail) throws InvalidAddressException
	{
		this(null, mail);
	}

	public SMTPAddress(String name, String mail) throws InvalidAddressException
	{
		this.name = name;
		if(mail.split("@").length != 2)
		{
			throw new InvalidAddressException();
		}
		this.mail = mail;
	}

	public static SMTPAddress fromText(String text) throws InvalidAddressException
	{
		if(text.startsWith("<") && text.endsWith(">"))
		{
			text = text.substring(1, text.length() - 1);
		}
		if(text.contains(" <") && text.endsWith(">"))
		{
			String[] arr = text.split(" <");
			if(arr.length != 2)
			{
				throw new InvalidAddressException();
			}
			String mail = arr[1];
			return new SMTPAddress(arr[0], mail.substring(0, mail.length() - 1));
		}
		else
		{
			return new SMTPAddress(null, text);
		}
	}

	public String getName()
	{
		return this.name;
	}

	public String getInboxName()
	{
		return this.mail.split("@")[0];
	}

	public String getDomain()
	{
		return this.mail.split("@")[1];
	}

	/**
	 * Gets servers responsible for this address, even when no MX record is set.
	 *
	 * @return Servers responsible for this address.
	 * @throws NamingException When a DNS error occurred.
	 */
	public String[] getMailServers() throws NamingException
	{
		String domainName = this.getDomain();
		InitialDirContext iDirC = new InitialDirContext();
		Attributes attributes = iDirC.getAttributes("dns:/" + domainName, new String[]{"MX"});
		Attribute attributeMX = attributes.get("MX");
		if(attributeMX == null)
		{
			return (new String[]{domainName});
		}
		String[][] pvhn = new String[attributeMX.size()][2];
		for(int i = 0; i < attributeMX.size(); i++)
		{
			pvhn[i] = ("" + attributeMX.get(i)).split("\\s+");
		}
		Arrays.sort(pvhn, (o1, o2)->(Integer.parseInt(o1[0]) - Integer.parseInt(o2[0])));
		String[] sortedHostNames = new String[pvhn.length];
		for(int i = 0; i < pvhn.length; i++)
		{
			sortedHostNames[i] = pvhn[i][1].endsWith(".") ? pvhn[i][1].substring(0, pvhn[i][1].length() - 1) : pvhn[i][1];
		}
		return sortedHostNames;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid()
	{
		return (name == null || !name.contains("<") && !name.contains(">") && !name.contains(",")) && !mail.contains("<") && !mail.contains(">") && !mail.contains(",");
	}

	/**
	 * Returns a valid copy of the SMTPAddress.
	 *
	 * @return a valid copy of the SMTPAddress.
	 */
	public SMTPAddress validCopy()
	{
		try
		{
			return new SMTPAddress(name.replaceAll("<", "-").replaceAll(">", "-").replaceAll(",", ""), mail.replaceAll("<", "-").replaceAll(">", "-").replaceAll(",", ""));
		}
		catch(InvalidAddressException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns an unchanged copy of the SMTPAddress.
	 *
	 * @return an unchanged copy of the SMTPAddress.
	 */
	public SMTPAddress copy()
	{
		try
		{
			return new SMTPAddress(name, mail);
		}
		catch(InvalidAddressException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String toString()
	{
		if(name != null)
		{
			return name + " <" + mail + ">";
		}
		else
		{
			return mail;
		}
	}
}
