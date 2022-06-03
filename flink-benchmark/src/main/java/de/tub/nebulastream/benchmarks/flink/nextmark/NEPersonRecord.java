package de.tub.nebulastream.benchmarks.flink.nextmark;

public class NEPersonRecord {

    public long id;
    public char[] name;
    public char[] emailAddress;
    public char[] creditCard;
    public char[] city;
    public char[] dateTime;

    public NEPersonRecord() {}


    public NEPersonRecord(long id, char[] name, char[] emailAddress, char[] creditCard, char[] city, char[] dateTime) {
        this.id = id;
        this.name = name;
        this.emailAddress = emailAddress;
        this.creditCard = creditCard;
        this.city = city;
        this.dateTime = dateTime;
    }
}
