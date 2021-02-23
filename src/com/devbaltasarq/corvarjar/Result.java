// CorvarJar (c) 2020 Baltasar MIT License <jbgarcia@uvigo.es>


package com.devbaltasarq.corvarjar;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;


/** Represents the results of a given experiment. */
public class Result extends Persistent {
    private static final String FIELD_TAG = "tag";
    private static final String FIELD_TIME = "time";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_RR = "rr";
    private static final String FIELD_RRS = "rrs";
    private static final String FIELD_EVENTS = "events";
    private static final String FIELD_TYPE_ID = "type_id";
    private static final String FIELD_EVENT_TYPE = "event_type";
    private static final String FIELD_HEART_BEAT_AT = "heart_beat_at";
    private static final String FIELD_ELAPSED_TIME = "elapsed_time";


    private static final String LOG_TAG = Result.class.getSimpleName();

    public static class BeatEvent {
        public BeatEvent(long time, long rr)
        {
            this.time = time;
            this.rr = rr;
        }

        public long getTime()
        {
            return this.time;
        }

        public long getRR()
        {
            return this.rr;
        }

        private long time;
        private long rr;
    }

    public static class Builder {
        public Builder(long dateTime)
        {
            this.dateTime = dateTime;
            this.rrs = new ArrayList<>();
        }

        /** Adds a new Event to the list.
          * @param beat the new time, rr pair.
          */
        public void add(BeatEvent beat)
        {
            this.rrs.add( beat );
        }

        /** Adds all the given events.
          * @param beats a collection of time, rr pairs.
          */
        public void addAll(Collection<BeatEvent> beats)
        {
            this.rrs.addAll( beats );
        }

        /** Clears all the stored events. */
        public void clear()
        {
            this.rrs.clear();
        }

        /** @return all stored hearbeats up to this moment. */
        public BeatEvent[] getAllRRs()
        {
            return this.rrs.toArray( new BeatEvent[ 0 ] );
        }

        public Result build(long elapsedMillis)
        {
            return this.build( Tag.NO_TAG, elapsedMillis );
        }

        /** @return the appropriate Result object, given the current data.
          * @param elapsedMillis the length of the measurement
          * @param tag the tag for this measurement
          * @see Result
          */
        public Result build(Tag tag, long elapsedMillis)
        {
            return new Result(
                            tag,
                            Id.create(),
                            this.dateTime,
                            elapsedMillis,
                            this.rrs.toArray( new BeatEvent[ 0 ]) );
        }

        private long dateTime;
        private ArrayList<BeatEvent> rrs;
    }


    /** Creates a new Result, in which the events of the experiment will be stored.
     * @param tag  the tag for the result.
     * @param id   the id of the result.
     * @param dateTime the moment (in millis) this experiment was collected.
     * @param rrs the collection of time/rr's pairs.
     */
    private Result(Tag tag, Id id, long dateTime, long durationInMillis, BeatEvent[] rrs)
    {
        super( id );

        this.tag = tag;
        this.durationInMillis = durationInMillis;
        this.dateTime = dateTime;
        this.rrs = rrs;
    }

    @Override
    public TypeId getTypeId()
    {
        return TypeId.Result;
    }

    /** @return the date for this result. */
    public long getTime()
    {
        return this.dateTime;
    }

    /** @return the tag for this result. */
    public Tag getTag() { return this.tag; }

    @Override
    public int hashCode()
    {
        return ( 11 * this.getId().hashCode() )
                + Long.valueOf( 13 * this.getDurationInMillis() ).hashCode()
                + ( 17 * this.rrs.length )
                + ( 23 * this.getTag().hashCode() );
    }

    @Override
    public boolean equals(Object o)
    {
        boolean toret = false;

        if ( o instanceof Result ) {
            Result ro = (Result) o;

            if ( this.getTag().equals( ro.getTag() )
              && this.getDurationInMillis() == ro.getDurationInMillis()
              && this.rrs.length == ro.rrs.length )
            {
                toret = true;

                for(int i = 0; i < this.rrs.length; ++i) {
                    if ( this.rrs[ i ] != ro.rrs[ i ] ) {
                        toret = false;
                        break;
                    }
                }

            }
        }

        return toret;
    }

    /** @return all rr's in this result. Warning: the list can be huge. */
    public BeatEvent[] getRRsCopy()
    {
        return Arrays.copyOf( this.rrs, this.rrs.length );
    }

    /** Creates the standard pair of text files, one for heatbeats,
      * and another one to know when the activity changed.
      */
    public void exportToStdTextFormat(Writer beatsStream) throws IOException
    {
        // Run all over the rr's and scatter them on files
        for (BeatEvent rr : this.rrs) {
            beatsStream.write( Long.toString( rr.getRR() ) );
            beatsStream.write( '\n' );
        }

        return;
    }

    /** @return the number of rr's stored. */
    public int size()
    {
        return this.rrs.length;
    }

    public String getResultFileName()
    {
        return buildResultFileName( this );
    }

    /** @return the duration in millis. Will throw if the experiment is not finished yet. */
    public long getDurationInMillis()
    {
        return this.durationInMillis;
    }

    @Override
    public String toString()
    {
        return this.getId() + "@" + this.getTime() + ": " + this.getTag()
                + " - " + new Duration( this.getDurationInMillis() ).toChronoString();
    }

    @Override
    public void writeToJSON(JsonWriter jsonWriter) throws IOException
    {
        this.writeIdToJSON( jsonWriter );
        jsonWriter.name( FIELD_TAG ).value( this.getTag().toString() );
        jsonWriter.name( FIELD_DATE ).value( this.getTime() );
        jsonWriter.name( FIELD_TIME ).value( this.getDurationInMillis() );

        jsonWriter.name( FIELD_RRS ).beginArray();
        for(BeatEvent rr: this.rrs) {
            jsonWriter.beginObject();
            jsonWriter.name( FIELD_RR ).value( rr.getRR() );
            jsonWriter.name( FIELD_TIME ).value( rr.getTime() );
            jsonWriter.endObject();
        }

        jsonWriter.endArray();
    }

    public static Result fromJSON(Reader reader) throws JsonParseException
    {
        final JsonReader JSON_READER = new JsonReader( reader );
        final ArrayList<BeatEvent> RRS = new ArrayList<>();
        Result toret;
        long durationInMillis = -1L;
        TypeId typeId = null;
        Id id = null;
        Tag tag = Tag.NO_TAG;
        long dateTime = -1L;

        // Load data
        try {
            JSON_READER.beginObject();
            while ( JSON_READER.hasNext() ) {
                final String NEXT_NAME = JSON_READER.nextName();

                if ( NEXT_NAME.equals( FIELD_TAG ) ) {
                    tag = new Tag( JSON_READER.nextString() );
                }
                else
                if ( NEXT_NAME.equals( FIELD_DATE ) ) {
                    dateTime = JSON_READER.nextLong();
                }
                else
                if ( NEXT_NAME.equals( FIELD_TIME ) ) {
                    durationInMillis = JSON_READER.nextLong();
                }
                else
                if ( NEXT_NAME.equals( FIELD_TYPE_ID ) ) {
                    typeId = readTypeIdFromJson( JSON_READER );
                }
                else
                if ( NEXT_NAME.equals( Id.FIELD ) ) {
                    id = readIdFromJSON( JSON_READER );
                }
                else
                if ( NEXT_NAME.equals( FIELD_RRS )
                  || NEXT_NAME.equals( FIELD_EVENTS ) )
                {
                    JSON_READER.beginArray();

                    // Read each time, rr pair
                    while( JSON_READER.hasNext() ) {
                        long time = -1;
                        long rr = -1;
                        String eventType = "N/A";

                        JSON_READER.beginObject();

                        // Read the individual time, rr object.
                        while( JSON_READER.hasNext() ) {
                            final String PAIR_NEXT_NAME = JSON_READER.nextName();

                            if ( PAIR_NEXT_NAME.equals( FIELD_EVENT_TYPE ) ) {
                                eventType = JSON_READER.nextString();
                            }
                            else
                            if ( PAIR_NEXT_NAME.equals( FIELD_TIME )
                              || PAIR_NEXT_NAME.equals( FIELD_ELAPSED_TIME ) )
                            {
                                time = JSON_READER.nextLong();
                            }
                            else
                            if ( PAIR_NEXT_NAME.equals( FIELD_RR )
                              || PAIR_NEXT_NAME.equals( FIELD_HEART_BEAT_AT ) )
                            {
                                rr = JSON_READER.nextLong();
                            } else {
                                JSON_READER.skipValue();
                            }
                        }

                        JSON_READER.endObject();

                        if ( time >= 0
                          && rr >= 0 )
                        {
                            RRS.add( new BeatEvent( time, rr ) );
                        } else {
                            Log.i( LOG_TAG,"ignored entry with no rr, even_type: "
                                    + eventType );
                        }
                    }

                    JSON_READER.endArray();
                } else {
                    JSON_READER.skipValue();
                }
            }
        } catch(IOException exc)
        {
            final String ERROR_MSG = "Creating result from JSON: " + exc.getMessage();

            Log.e(LOG_TAG, ERROR_MSG );
            throw new JsonParseException( ERROR_MSG );
        }

        // Chk
        if ( id == null
          || dateTime < 0
          || durationInMillis < 0
          || typeId != TypeId.Result )
        {
            final String MSG = "Creating result from JSON: invalid or missing data.";

            Log.e(LOG_TAG, MSG );
            throw new JsonParseException( MSG );
        } else {
            toret = new Result( tag,
                                id,
                                dateTime,
                                durationInMillis,
                                RRS.toArray( new BeatEvent[ 0 ] ) );
        }

        return toret;
    }

    /** Creates the result name. This name contains important info.
      * @param res The result to build a name for.
      */
    static String buildResultFileName(Result res)
    {
        return TypeId.Result.toString().toLowerCase()
                + "-i" + res.getId()
                + "-g" + res.getTag().toString()
                + "-t" + res.getTime()
                + ".res";
    }

    /** @return the result's time - date, reading it from its name.
     * @param resName the name of the result to extract the time from.
     */
    public static long parseTimeFromName(String resName)
    {
        final String STR_TIME = parseName( resName )[ 3 ];

        if ( STR_TIME.charAt( 0 ) != 't' ) {
            throw new Error( "malformed result name looking for time: "
                    + STR_TIME
                    + "/" + resName );
        }

        return Long.parseLong( STR_TIME.substring( 1 ) );
    }

    /** @return the result's tag, reading it from its name.
     * @param resName the name of the result to extract the time from.
     */
    public static String parseTagFromName(String resName)
    {
        final String STR_TAG = parseName( resName )[ 2 ];

        if ( STR_TAG.charAt( 0 ) != 'g' ) {
            throw new Error( "malformed result name looking for time: "
                    + STR_TAG
                    + "/" + resName );
        }

        return STR_TAG.substring( 1 );
    }

    private static String[] parseName(String resName)
    {
        if ( resName == null
          || resName.isEmpty() )
        {
            resName = "";
        }

        resName = resName.trim();

        // Remove extension
        resName = resName.substring( 0, resName.lastIndexOf( '.' ) );

        // Divide in parts
        final String[] TORET = resName.split( "-" );

        if ( TORET.length != 4 ) {
            throw new Error( "dividing result name in parts" );
        }

        return TORET;
    }

    private Tag tag;
    private long durationInMillis;
    private long dateTime;
    private BeatEvent[] rrs;
}
