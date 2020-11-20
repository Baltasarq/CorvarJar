package com.devbaltasarq.corvarjartest;

import com.devbaltasarq.corvarjar.LibInfo;
import com.devbaltasarq.corvarjar.ResultAnalyzer;

public class CorvarJarTest {
    public static void main(String[] args)
    {
        System.out.println( "CorvarJarTest (c) 2020 Baltasar Mile Group MIT License <jbgarcia@uvigo.es>" );
        System.out.println( LibInfo.asString() + "\n" );

        if ( args.length > 0 ) {
            final StringBuilder log = new StringBuilder();
            boolean verbose = false;
            int fileArgNumber = 0;

            if ( args[ 0 ].equalsIgnoreCase( "--verbose" ) ) {
                ++fileArgNumber;
                verbose = true;
            }

            ResultAnalyzer resultAnalyzer = new ResultAnalyzer( args[ fileArgNumber ] );

            if ( verbose ) {
                resultAnalyzer.setLog( log );
            }

            resultAnalyzer.analyze();

            System.out.println( "Stress level: " + resultAnalyzer.getStressLevel() );
            System.out.println( "MADRR       : " + resultAnalyzer.getMadRR() );
            System.out.println( "ApEn        : " + resultAnalyzer.getApEn()  + "\n" );
            System.out.print( "ProbeStress : " + resultAnalyzer.getProbeStress() );
            System.out.println( "\n\t(values > 0.5 indicate stress.)" );

            if ( verbose ) {
                System.out.println( "\nReport:" );
                System.out.println( log.toString() );
            }
        } else {
            System.out.println( "Usage: corvarjartest <filename.res>" );
        }
    }
}
