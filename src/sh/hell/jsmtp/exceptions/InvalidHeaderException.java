package sh.hell.jsmtp.exceptions;

public class InvalidHeaderException extends SMTPException
{
	public InvalidHeaderException(String message)
	{
		super(message);
	}
}
