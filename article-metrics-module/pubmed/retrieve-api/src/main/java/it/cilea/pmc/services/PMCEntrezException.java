package it.cilea.pmc.services;

public class PMCEntrezException extends Exception
{
    public PMCEntrezException()
    {
        super();
    }
    
    public PMCEntrezException(String message)
    {
        super(message);
    }
    
    public PMCEntrezException(String message, Throwable rootCause)
    {
        super(message, rootCause);
    }
}
