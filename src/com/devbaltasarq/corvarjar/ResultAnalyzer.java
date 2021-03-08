// CorvarJar (c) 2020 Baltasar MIT License <jbgarcia@uvigo.es>


package com.devbaltasarq.corvarjar;

import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;


public class ResultAnalyzer {
    private static String LOG_TAG = ResultAnalyzer.class.getSimpleName();
    
    // Stress level calculation constants
    private static final float STRESS_LEVEL_A1 = -8.64502f;
    private static final float STRESS_LEVEL_A2 = -0.01312f;
    private static final float STRESS_LEVEL_A3 = 0.04295f;
    private static final float STRESS_LEVEL_A4 = -0.01223f;
    private static final float STRESS_LEVEL_INDEPENDENT_TERM = 5.97785f;

    public ResultAnalyzer(String fileName)
    {
        this.fileName = fileName;
        this.log = null;
    }

    public boolean isVerbose()
    {
        return this.log != null;
    }

    public void setLog(StringBuilder log)
    {
        this.log = log;
    }

    public void analyze()
    {
        try {
            this.load( this.fileName );

            this.report = this.createHeader();

            // Loads data into dataRRnf and episodes (unfiltered RR in milliseconds)
            this.dataRRnf = new ArrayList<>();

            this.loadDataOnLists();

            if ( this.dataRRnf.size() > 0 ) {
                // Generates dataHRnf (unfiltered sequence of BPS values)
                dataHRnf = new ArrayList<>();

                for (int i = 0; i< dataRRnf.size(); i++) {
                    dataHRnf.add( 60.0f / ( dataRRnf.get(i) / 1000.0f ) );
                }

                if ( this.isVerbose() ) {
                    this.dumpList( this.dataHRnf, log, "Unfiltered hr" );
                }

                // Calculates dataBeatTimesnf (unfiltered beat positions in seconds) from dataRRnf
                dataBeatTimesnf = new ArrayList<>();
                dataBeatTimesnf.add(dataRRnf.get(0)/1000.0f);
                for (int i=1; i<dataRRnf.size(); i++) {
                    dataBeatTimesnf.add(dataBeatTimesnf.get(i-1)+dataRRnf.get(i)/1000.0f);
                }

                // Filters beat times creating a sequence of RR intervals
                dataBeatTimes = new ArrayList<>(dataBeatTimesnf);
                dataHR = new ArrayList<>(dataHRnf);
                dataRR = new ArrayList<>(dataRRnf);
                this.filterData();


                Log.i( LOG_TAG,"Filtered sequence: " + dataBeatTimes.size() +" values" );
                Log.i( LOG_TAG,"Last beat position: "
                                + dataBeatTimes.get( dataBeatTimes.size() - 1 ) + " seconds" );

                // Creates a series of HR values linearly interpolated
                dataHRInterpX = new ArrayList<>();
                dataHRInterp = new ArrayList<>();
                this.interpolate();

                Log.i( LOG_TAG,"length of xinterp: "+ dataHRInterpX.size());
                Log.i( LOG_TAG,"First value: "+ dataHRInterpX.get( 0 ) );
                Log.i( LOG_TAG,"Last value: "+ dataHRInterpX.get( dataHRInterpX.size() - 1 ) );

                if( this.isVerbose() ) {
                    this.dumpList( this.dataRR, log, "Normalized rr" );
                    this.dumpList( this.dataHR, log, "Normalized hr" );
                }

                // Calculate stress level
                this.valueRMS = this.calculateRMSSD( this.dataRR );
                this.valueSTD = this.calculateSTD( this.dataRR );
                this.valuePNN50 = this.calculatePNN50( this.dataRR );
                this.valueMeanBPM = this.calculateMean( this.dataHR );

                // Calculate the median
                this.valueMADRR = this.calculateMADRR( this.dataRR );

                // Calculate the entropy
                this.valueApEn = this.calculateApEn( this.dataRR, 2, 0.2f );

                // Calculate stress level
                this.calculateStress();

                // Summarizes all the results
                this.report += this.createReport();
            } else {
                this.report += "Empty data.";
            }
        } catch(IOException | JsonParseException exc)
        {
            this.report = "Error reading result for file: " + fileName + ": " + exc.getMessage();
        }
    }

    private void load(String fileName) throws IOException, JsonParseException
    {

        try (FileInputStream fileInputStream = new FileInputStream(fileName);
             InputStreamReader inputStreamReader = new InputStreamReader(
                     fileInputStream,
                     StandardCharsets.UTF_8.newDecoder() );
             BufferedReader bufferedReader = new BufferedReader( inputStreamReader ))
        {
            this.result = Result.fromJSON( bufferedReader );
        } catch (IOException | JsonParseException exc) {
            Log.e(LOG_TAG, "Error reading result for file: " + fileName + " " + exc.getMessage());
            throw exc;
        }
    }

    private void loadDataOnLists()
    {
        // Init data holders
        this.dataRRnf = new ArrayList<>( result.size() );

        // Store all data
        for(Result.BeatEvent evt: this.result.getRRsCopy()) {
            this.dataRRnf.add( (float) evt.getRR() );
        }

        if ( this.isVerbose() ) {
            this.dumpList( this.dataRRnf, log, "Unfiltered rr" );
            Log.i( LOG_TAG,"Size of vector: " + this.dataRRnf.size() );
        }

        return;
    }

    private void filterData()
    {
        final int WIN_LENGTH = 50;
        final float MIN_BPM = 24.0f;
        final float MAX_BPM = 198.0f;
        final float U_LAST = 13.0f;
        final float U_MEAN = 1.5f * U_LAST;

        Log.i( LOG_TAG,"I'm going to filter the signal" );

        int index = 1;

        this.filteredData = 0;

        while ( index < ( dataHR.size() - 1 ) ) {
            List<Float> v = dataHR.subList( Math.max( index-WIN_LENGTH, 0 ),index );

            float MEAN_LAST_BEATS = 0.0f;  // M = mean(v)
            for (int i = 0 ; i < v.size(); i++) {
                MEAN_LAST_BEATS += v.get( i );
            }
            MEAN_LAST_BEATS = MEAN_LAST_BEATS / v.size();

            final float CURRENT_BEAT = this.dataHR.get( index );
            final float PREVIOUS_BEAT = this.dataHR.get( index - 1 );
            final float NEXT_BEAT = this.dataHR.get( index + 1 );
            final float RELATION_PREVIOUS_BEAT = 100
                    * Math.abs( ( CURRENT_BEAT - PREVIOUS_BEAT ) / PREVIOUS_BEAT );
            final float RELATION_NEXT_BEAT = 100
                    * Math.abs( ( CURRENT_BEAT - NEXT_BEAT ) / NEXT_BEAT );

            final float RELATION_MEAN_BEAT = 100
                    * Math.abs( ( CURRENT_BEAT - MEAN_LAST_BEATS ) / MEAN_LAST_BEATS );

            if ( ( RELATION_PREVIOUS_BEAT < U_LAST
                    || RELATION_NEXT_BEAT < U_LAST
                    || RELATION_MEAN_BEAT < U_MEAN )
                    && CURRENT_BEAT > MIN_BPM
                    && CURRENT_BEAT < MAX_BPM )
            {
                index += 1;
            } else {
                Log.i( LOG_TAG,"Removing beat index: " + index );

                index += 1;
                ++this.filteredData;
                this.dataHR.set( index, MEAN_LAST_BEATS );
                this.dataRR.set( index, 60.0f / MEAN_LAST_BEATS );
            }
        }

        return;
    }

    private void interpolate()
    {
        float xmin = dataBeatTimes.get(0);
        float xmax = dataBeatTimes.get(dataBeatTimes.size()-1);
        float step = 1.0f / freq;

        if ( dataBeatTimes.size() > 2 ) {
            int leftHRIndex, rightHRIndex;
            float leftBeatPos, rightBeatPos, leftHRVal, rightHRVal;
            leftHRIndex = 0;
            rightHRIndex = 1;
            leftBeatPos = dataBeatTimes.get( leftHRIndex );
            rightBeatPos = dataBeatTimes.get( rightHRIndex );
            leftHRVal = dataHR.get(leftHRIndex);
            rightHRVal = dataHR.get(rightHRIndex);

            // Calculates positions in x axis
            dataHRInterpX.add(xmin);
            float newValue = xmin+step;
            while (newValue<=xmax) {
                dataHRInterpX.add(newValue);
                newValue += step;
            }

            for (int xInterpIndex = 0; xInterpIndex< dataHRInterpX.size(); xInterpIndex++ )
            {
                if (dataHRInterpX.get(xInterpIndex) >= rightBeatPos) {
                    leftHRIndex++;
                    rightHRIndex++;
                    leftBeatPos = dataBeatTimes.get(leftHRIndex);
                    rightBeatPos = dataBeatTimes.get(rightHRIndex);
                    leftHRVal = dataHR.get(leftHRIndex);
                    rightHRVal = dataHR.get(rightHRIndex);
                }

                // Estimate HR value in position
                float HR = (rightHRVal-leftHRVal)*(dataHRInterpX.get(xInterpIndex)-leftBeatPos)/(rightBeatPos-leftBeatPos)+leftHRVal;
                dataHRInterp.add(HR);
            }
        } else {
            float xAxis = xmin;

            for(float dataBeatTime: this.dataBeatTimes) {
                this.dataHRInterp.add( dataBeatTime );
                this.dataHRInterpX.add( xAxis );

                xAxis += step;
            }
        }

        return;
    }

    private String createReport()
    {
        final StringBuilder TEXT = new StringBuilder( "<h3>Signal data</h3>" );
        final float FILTERED_RATE = 100.0f * ( dataRRnf.size() - dataRR.size() ) / dataRRnf.size();

        TEXT.append( "<p>&nbsp;&nbsp;<b>Length of original RR signal</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%d", dataRRnf.size()) );
        TEXT.append( " values</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>Length of filtered RR signal</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%d", dataRRnf.size() - this.filteredData) );
        TEXT.append( " values</p>" );

        TEXT.append( "<p>&nbsp;&nbsp;<b>Beat rejection rate</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", FILTERED_RATE) );
        TEXT.append( "%</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>Interpolation frequency</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", freq) );
        TEXT.append( " Hz</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>Number of interpolated samples</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%d", dataHRInterp.size()) );
        TEXT.append( "</p>" );

        // ------------------------

        TEXT.append( "<br/><h3>HRV time-domain results</h3>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>Mean RR (AVNN)</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", this.valueMeanBPM) );
        TEXT.append( " ms</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>STD RR (SDNN)</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", this.valueSTD) );
        TEXT.append( " ms</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>pNN50</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", this.valuePNN50) );
        TEXT.append( "%</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>rMSSD</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", this.valueRMS) );
        TEXT.append( " ms</p>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>normHRV</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", calculateNormHRV(dataRR)) );
        TEXT.append( "</p>" );

        // ------------------------
        final List<Float> POWER_BANDS = calculateSpectrum(dataHRInterpX.get(0), dataHRInterpX.get(dataHRInterpX.size()-1));

        TEXT.append( "<br/><h3>HRV frequency-domain results</h3>" );
        TEXT.append( "<p>&nbsp;&nbsp;<b>Total power</b>: " );
        TEXT.append( String.format( Locale.getDefault(), "%.2f", POWER_BANDS.get(0)) );
        TEXT.append( " ms&sup2;</p>" );

        if ( POWER_BANDS.get( 1 ) > 0.0 ) {
            TEXT.append( "<p>&nbsp;&nbsp;<b>LF power</b>: " );
            TEXT.append( String.format( Locale.getDefault(), "%.2f", POWER_BANDS.get(1)) );
            TEXT.append( " ms&sup2;</p>" );
        } else {
            TEXT.append( "<p>&nbsp;&nbsp;<b>LF power</b>: --</p>" );
        }

        if ( POWER_BANDS.get( 2 ) > 0.0 ) {
            TEXT.append( "<p>&nbsp;&nbsp;<b>HF power</b>: " );
            TEXT.append( String.format( Locale.getDefault(), "%.2f", POWER_BANDS.get(2)) );
            TEXT.append( " ms&sup2;</p>" );
        } else {
            TEXT.append( "<p>&nbsp;&nbsp;<b>HF power</b>: --</p>" );
        }

        if ( POWER_BANDS.get( 1 ) > 0.0 ) {
            TEXT.append( "<p>&nbsp;&nbsp;<b>LF/HF ratio</b>: " );
            TEXT.append( String.format( Locale.getDefault(), "%.2f", POWER_BANDS.get(3)) );
            TEXT.append( "</p>" );
        } else {
            TEXT.append( "<p>&nbsp;&nbsp;<b>LF/HF ratio</b>: --</p>" );
        }

        TEXT.append( "<br/><h3>Stress level</h3>" );
        TEXT.append( "<p>&nbsp;&nbsp;Stress (0 - 1): " );
        TEXT.append( this.valueStress);
        TEXT.append( "</p>" );

        TEXT.append( "<br/><h3>MadRR</h3>" );
        TEXT.append( "<p>&nbsp;&nbsp;MadRR: " );
        TEXT.append( this.valueMADRR);
        TEXT.append( "ms.</p>" );

        TEXT.append( "<br/><h3>ApEn</h3>" );
        TEXT.append( "<p>&nbsp;&nbsp;ApEn: " );
        TEXT.append( this.valueApEn);
        TEXT.append( "ms.</p>" );

        return TEXT.toString();
    }

    private float calculateMean(List<Float> signal)
    {
        float sum = 0.0f;

        for (int i=0 ; i < signal.size() ; i++) {
            sum += signal.get(i);
        }

        return sum/signal.size();
    }

    private float calculateSTD(List<Float> signal)
    {
        float std = 0.0f;

        for (int i=1 ; i < signal.size() ; i++) {
            std += Math.pow(signal.get(i)-calculateMean(signal),2);
        }

        std /= (signal.size()-1);
        std = (float) Math.sqrt(std);
        return std;
    }

    private float calculateRMSSD(List<Float> signal)
    {
        float rrdifs2 = 0.0f;

        for (int i=1 ; i < signal.size() ; i++) {
            rrdifs2 += Math.pow((signal.get(i) - signal.get(i-1)),2);
        }

        return (float) Math.sqrt(rrdifs2/(signal.size()-1));
    }

    private float calculateNormHRV(List<Float> signal)
    {
        float lnrMSSD = (float) Math.log(calculateRMSSD(signal));
        return lnrMSSD*100.0f/6.5f;
    }

    private float calculatePNN50(List<Float> signal)
    {
        int numIntervals = 0;
        int numBigIntervals = 0;
        for (int i=1 ; i < signal.size() ; i++) {
            numIntervals++;
            if (Math.abs(signal.get(i)-signal.get(i-1)) > 50.0) {
                numBigIntervals++;
            }
        }

        return 100.0f * ((float)numBigIntervals/(float)numIntervals);
    }

    private List<Float> calculateSpectrum(Float begSegment, Float endSegment)
    {
        Log.i(LOG_TAG + ".Spec","Calculating spectrum");
        Log.i(LOG_TAG + ".Spec","Minimum time: " + begSegment + " seconds");
        Log.i(LOG_TAG + ".Spec","Maximum time: " + endSegment + " seconds");
        float analysisWindowLength = ( endSegment-begSegment ) / 3.0f;
        Log.i(LOG_TAG + ".Spec","Analysis window length: "+ analysisWindowLength +" seconds");

        // Five windows, length 1/3 of signal, overlap 50%

        float beg[] = new float[5];
        float end[] = new float[5];

        beg[0] = begSegment;
        end[0] = beg[0] + analysisWindowLength;

        for (int index=1 ; index < 5; index++ ) {
            beg[index] = beg[index-1] + analysisWindowLength / 2.0f;
            end[index] = beg[index] + analysisWindowLength;
        }

        for (int index=0; index < 5; index++) {
            Log.i(LOG_TAG + ".Spec","Window number "+ (index+1) +": ("+ beg[index] + "," + end[index] +") seconds");
        }

        int maxSegmentLength = 0;
        for (int index=0; index<5; index++) {
            List<Float> segmentTMP;
            segmentTMP = getSegmentHRInterp(beg[index],end[index]);
            if ( segmentTMP.size() > maxSegmentLength )
                maxSegmentLength = segmentTMP.size();
        }

        int paddedLength = (int) Math.pow(2,(int) Math.ceil(Math.log((double) maxSegmentLength) / Math.log(2.0)));

        Log.i(LOG_TAG + ".Spec","Max segment length: "+maxSegmentLength);
        Log.i(LOG_TAG + ".Spec","Padded length: "+paddedLength);


        List<Float> SpectrumAvg = new ArrayList<>();
        int SpectrumLength = paddedLength/2;


        for (int windowIndex=0 ; windowIndex<5 ; windowIndex++) {
            List<Float> RRSegment = getSegmentHRInterp(beg[windowIndex],end[windowIndex]);
            for  (int index=0 ; index < RRSegment.size() ; index++) {
                RRSegment.set(index,1000.0f/(RRSegment.get(index)/60.f));
            }
            Log.i(LOG_TAG + ".Spec", "Segment "+(windowIndex+1)+" - number of samples: "+RRSegment.size());
            double avg = 0.0;
            for  (int index=0 ; index < RRSegment.size() ; index++) {
                avg += RRSegment.get(index);
            }
            avg = avg / RRSegment.size();
            for  (int index=0 ; index < RRSegment.size() ; index++) {
                RRSegment.set( index , (float) (RRSegment.get(index)-avg) );
            }

            double[] hamWindow = makeHammingWindow(RRSegment.size());
            for (int index=0 ; index < RRSegment.size() ; index++) {
                RRSegment.set(index, (float) (RRSegment.get(index)*hamWindow[index]));
            }

            // writeFile("timeSignal.txt", RRSegment);

            double[] RRSegmentPaddedX = padSegmentHRInterp(RRSegment, paddedLength);
            //corvarjar.Log.i(LogTag + ".Spec", "Length of padded array: "+RRSegmentPaddedX.length);

            /*
            List<Double> RRSPtmp = new ArrayList<>();
            for (double rrPaddedX: RRSegmentPaddedX) {
                RRSPtmp.add( rrPaddedX );
            }
            writeFile("timeSignalPadded.txt", RRSPtmp);
            */
            double[] RRSegmentPaddedY = new double[RRSegmentPaddedX.length];

            fft(RRSegmentPaddedX,RRSegmentPaddedY, RRSegmentPaddedX.length);
            //corvarjar.Log.i(LogTag + ".Spec","Length of fft: "+RRSegmentPaddedX.length);

            List<Float> Spectrum = new ArrayList<>();
            for (int index=0 ; index<SpectrumLength ; index++) { // Only positive half of the spectrum
                Spectrum.add((float) (Math.pow(RRSegmentPaddedX[index],2)+Math.pow(RRSegmentPaddedY[index],2)));
            }
            Log.i(LOG_TAG + ".Spec","Length of spectrum: "+Spectrum.size());

            if (windowIndex==0) {
                for (int index=0 ; index<SpectrumLength ; index++) {
                    SpectrumAvg.add(Spectrum.get(index));
                }
            } else {
                for (int index=0 ; index<SpectrumLength ; index++) {
                    float newValue = SpectrumAvg.get(index)+Spectrum.get(index);
                    SpectrumAvg.set(index,newValue);
                }
            }

        }  // for windowIndex

        for (int index=0 ; index<SpectrumLength ; index++) {
            SpectrumAvg.set(index,SpectrumAvg.get(index)/5.0f);
        }

        List<Float> SpectrumAxis = new ArrayList<>();
        for (int index=0 ; index<SpectrumLength ; index++) { // Only positive half of the spectrum
            SpectrumAxis.add(index*(freq/2)/(SpectrumLength-1));
        }

        // writeFile("Spectrum.txt", SpectrumAvg);

        Log.i(LOG_TAG + ".Spec","Length of spectrum axis: "+SpectrumAxis.size());

        if ( SpectrumAxis.size() > 0 ) {
            Log.i(LOG_TAG + ".Spec","First sample of spectrum axis: "+SpectrumAxis.get(0));
            Log.i(LOG_TAG + ".Spec","Last sample of spectrum axis: "+SpectrumAxis.get(SpectrumLength-1));
        }

        List<Float> results = new ArrayList<>();

        float totalPower = powerInBand(SpectrumAvg, SpectrumAxis, totalPowerBeg, totalPowerEnd);

        results.add(totalPower);

        Log.i(LOG_TAG + ".Spec", "Total power: "+totalPower);

        float LFPower;
        if ((endSegment-begSegment) > 40.0) {
            // Minimum freq. in LF band is 0.05 Hz. Two cycles are required to estimate power
            LFPower = powerInBand(SpectrumAvg, SpectrumAxis, LFPowerBeg, LFPowerEnd);
        } else {
            LFPower = -1.0f;
        }
        results.add(LFPower);
        Log.i(LOG_TAG + ".Spec", "LF power: "+LFPower);

        float HFPower;
        if ((endSegment-begSegment) > 13.33) {
            HFPower = powerInBand(SpectrumAvg, SpectrumAxis, HFPowerBeg, HFPowerEnd);
        } else {
            HFPower = -1.0f;
        }
        results.add(HFPower);
        Log.i(LOG_TAG + ".Spec", "HF power: "+HFPower);
        Log.i(LOG_TAG + ".Spec", "LF/HF ratio: "+LFPower/HFPower);
        results.add(LFPower/HFPower);

        return results;
    }

    private void fft(double[] x, double[] y, int n)
    {
        int i,j,k,n2,a;
        int n1;
        double c,s,t1,t2;

        int m = (int)(Math.log(n) / Math.log(2));

        double[] cos = new double[n/2];
        double[] sin = new double[n/2];
        for(int index=0; index<n/2; index++) {
            cos[index] = Math.cos(-2*Math.PI*index/n);
            sin[index] = Math.sin(-2*Math.PI*index/n);
        }

        // Bit-reverse
        j = 0;
        n2 = n/2;
        for (i=1; i < n - 1; i++) {
            n1 = n2;
            while ( j >= n1 ) {
                j = j - n1;
                n1 = n1/2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n2 = 1;

        for (i=0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j=0; j < n1; j++) {
                c = cos[a];
                s = sin[a];
                a +=  1 << (m-i-1);

                for (k=j; k < n; k=k+n2) {
                    t1 = c*x[k+n1] - s*y[k+n1];
                    t2 = s*x[k+n1] + c*y[k+n1];
                    x[k+n1] = x[k] - t1;
                    y[k+n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

    private double[] makeHammingWindow(int windowLength)
    {
        // Make a Hamming window:
        // w(n) = a0 - (1-a0)*cos( 2*PI*n/(N-1) )
        // a0 = 25/46
        double a0 = 25.0/46.0;
        double[] window = new double[windowLength];

        for(int i = 0; i < windowLength; i++)
            window[i] = a0 - (1-a0) * Math.cos(2*Math.PI*i/(windowLength-1));

        return window;
    }

    private float powerInBand(List<Float> spectrum, List<Float> spectrumAxis, float begFreq, float endFreq)
    {
        float pp = 0.0f;

        for (int index=0 ; index < spectrum.size() ; index++) {
            if ( (spectrumAxis.get(index)>=begFreq) && (spectrumAxis.get(index)<=endFreq) ) {
                pp = pp + spectrum.get(index);
            }
        }

        pp = pp * hammingFactor;
        pp = pp / (float)(2.0f*Math.pow(spectrum.size(),2.0f));
        return pp;
    }

    private List<Float> getSegmentHRInterp(float beg, float end)
    {
        List<Float> segment = new ArrayList<>();

        for (int indexHR=0 ; indexHR<dataHRInterp.size() ; indexHR++) {
            if  ( (dataHRInterpX.get(indexHR) >= beg) && (dataHRInterpX.get(indexHR) <= end) ) {
                segment.add(dataHRInterp.get(indexHR));
            }
        }

        return segment;
    }

    private double[] padSegmentHRInterp(List<Float> hrSegment, int newLength)
    {
        double[] segmentPadded = new double[newLength];

        for (int index = 0 ; index < hrSegment.size() ; index++) {
            segmentPadded[index] =  (double) (hrSegment.get(index));
        }

        return segmentPadded;
    }

    /** @return the MADDRR (median) value. */
    private float calculateMADRR(List<Float> signal)
    {
        List<Float> difsRR = new ArrayList<>();
        float result;

        for (int i=1 ; i < signal.size() ; i++) {
            difsRR.add(Math.abs(signal.get(i) - signal.get(i-1)));
        }
        Collections.sort(difsRR);
        int n = difsRR.size() / 2;

        if (difsRR.size() % 2 == 0)
            result = ( difsRR.get(n) + difsRR.get(n-1) )/2;
        else
            result = difsRR.get(n);

        return result;
    }

    /** @return the entropy. */
    private float calculateApEn(final List<Float> SIGNAL, int m, float r)
    {
        r *= _calculateSD( SIGNAL );
        return Math.abs( _phi( SIGNAL, m + 1, r )  - _phi( SIGNAL, m, r ) );
    }

    private float _calculateSD(final List<Float> SIGNAL)
    {
        float sum = 0.0f, standardDeviation = 0.0f;
        int length = SIGNAL.size();

        for(int index=0 ; index < length ; index++) {
            sum += SIGNAL.get(index);
        }

        float mean = sum/length;

        for(int index=0 ; index < length ; index++) {
            standardDeviation += Math.pow(SIGNAL.get(index) - mean, 2);
        }

        return (float) Math.sqrt(standardDeviation/length);
    }

    private float _phi(final List<Float> U, int m, float r)
    {
        int N = U.size();
        ArrayList<ArrayList<Float>> x = new ArrayList<>();
        for (int i=0; i < N-m+1 ; i++) {
            ArrayList<Float> x_row = new ArrayList<>();
            for (int j=i; j<i+m ; j++) {
                x_row.add(U.get(j));
            }
            x.add(x_row);
        }

        ArrayList<Float> C = new ArrayList<>();
        for (int i=0 ; i<x.size() ; i++)
        {
            float C_tmp = .0f;
            for (int j=0 ; j<x.size() ; j++)
            {
                if (_maxdist(x.get(i),x.get(j)) <= r)
                {
                    C_tmp += 1.0f;
                }
            }
            C.add(C_tmp / (N-m+1.0f));
        }

        float result = .0f;
        for (int index=0 ; index<C.size() ; index++) {
            result += Math.log(C.get(index));
        }
        result /= (N - m + 1.0f);
        return result;
    }

    private float _maxdist(final List<Float> X_I , final List<Float> X_J)
    {
        ArrayList<Float> diffs = new ArrayList<>();
        for (int index = 0; index < X_I.size(); index++) {
            diffs.add(Math.abs(X_I.get(index)-X_J.get(index)));
        }
        return Collections.max(diffs);
    }

    /** Calculates the stress level. */
    private void calculateStress()
    {
        final float TERM1 = STRESS_LEVEL_A1 * this.valueApEn;
        final float TERM2 = STRESS_LEVEL_A2 * this.valueMADRR;
        final float TERM3 = STRESS_LEVEL_A3 * this.valueMeanBPM;
        final float TERM4 = STRESS_LEVEL_A4 * this.valuePNN50;

        this.valueStress = TERM1 + TERM2 + TERM3 + TERM4 + STRESS_LEVEL_INDEPENDENT_TERM;

        if ( this.isVerbose() ) {
            this.log.append( "\nApEn: " + this.valueApEn);
            this.log.append( "\nMADRR: " + this.valueMADRR);
            this.log.append( "\nMean BPM: " + this.valueMeanBPM );
            this.log.append( "\nPNN50: " + this.valuePNN50 );
            this.log.append( "\nStress level:" );
            this.log.append( "\n\tterm1: " + TERM1
                             + "\n\t+ term2: " + TERM2
                             + "\n\t+ term3: " + TERM3
                             + "\n\t+ term4: " + TERM4
                             + "\n\t+ indep: " + STRESS_LEVEL_INDEPENDENT_TERM
                             + "\n\t= " + this.valueStress );
        }

        return;
    }

    /** @return a value between 0 and 1. Values > .5 indicate stress. */
    public float getProbeStress()
    {
        final float ODDS_RATIO = (float) Math.exp( this.valueStress );

        return ( ODDS_RATIO / ( ODDS_RATIO + 1 ) );
    }

    public String getReport()
    {
        return this.report;
    }

    /**
     * Returns the calculated stress
     * 0 or less (negative) : no stress at all.
     * 1 or more: (positive): absolute stress.
     * an intermediate value 0-1, e.g. 0.43 some stress.
     * @return a value between 0 (or less) (no stress at all)
     *         to 1 or more (very stressed).
     */
    public float getStressLevel()
    {
        return this.valueStress;
    }

    /** @return the median of RR (MADRR value), in ms. */
    public float getMadRR()
    {
        return this.valueMADRR;
    }

    /** @return the entropy. */
    public float getApEn()
    {
        return this.valueApEn;
    }

    /** @return the HR sequence, interpolated. */
    public Float[] getDataHRInterpolated()
    {
        return this.dataHRInterp.toArray( new Float[ 0 ] );
    }

    /** @return the HR sequence, interpolated for time. */
    public Float[] getDataHRInterpolatedForX()
    {
        return this.dataHRInterpX.toArray( new Float[ 0 ] );
    }

    private String createHeader()
    {
        final SimpleDateFormat FORMATTER = new SimpleDateFormat( "dd/MM/yyyy HH:mm:ss",
                                                                  Locale.getDefault() );
        long timeInMillis = this.result.getTime();
        Calendar time = Calendar.getInstance();

        time.setTimeInMillis( timeInMillis );

        return "Tag: " + this.result.getTag().toString()
                + "\nTime: " + FORMATTER.format( time.getTime() )
                + "\n\n";
    }

    private void dumpList(final List<Float> DATA, StringBuilder log, String label)
    {
        float avg = 0;
        String delim = "";

        log.append( "\n\n" );
        log.append( label );
        log.append( ": [" );

        for(float v: DATA) {
            log.append( delim );
            log.append( String.format( "%5.2f", v ) );

            avg += v;
            delim = ", ";
        }

        log.append( "]\n" );
        log.append( "Avg: " );
        log.append( String.format( "%5.2f", avg / DATA.size() ) );
        log.append( '\n' );
    }

    private String fileName;
    private Result result;
    private String report;
    private StringBuilder log;

    private List<Float> dataRRnf;
    private List<Float> dataHRnf;
    private List<Float> dataBeatTimesnf;
    private List<Float> dataBeatTimes;
    private List<Float> dataRR;
    private List<Float> dataHR;
    private List<Float> dataHRInterpX;
    private List<Float> dataHRInterp;
    private int filteredData;
    private float valueStress;
    private float valueApEn;
    private float valueMADRR;
    private float valueSTD;
    private float valueRMS;
    private float valueMeanBPM;
    private float valuePNN50;

    private static float freq = 4.0f;                   // Interpolation frequency in hz.
    private static float hammingFactor = 1.586f;

    private static float totalPowerBeg = 0.0f;
    private static float totalPowerEnd = 4.0f/2.0f;     // Beginning and end of total power band

    private static float LFPowerBeg = 0.05f;
    private static float LFPowerEnd = 0.15f;            // Beginning and end of LF band

    private static float HFPowerBeg = 0.15f;
    private static float HFPowerEnd = 0.4f;             // Beginning and end of HF band
}
