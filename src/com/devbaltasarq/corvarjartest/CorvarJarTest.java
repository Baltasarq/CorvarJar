package com.devbaltasarq.corvarjartest;

import com.devbaltasarq.corvarjar.ResultAnalyzer;

public class CorvarJarTest {
    public static void main(String[] args)
    {
        System.out.println( "CorvarJarTest (c) 2020 Baltasar Mile Group MIT License <jbgarcia@uvigo.es>" );

        if ( args.length > 0 ) {
            ResultAnalyzer resultAnalyzer = new ResultAnalyzer( args[ 0 ] );

            resultAnalyzer.analyze();

            System.out.println( "Stress level: " + resultAnalyzer.getStressLevel() );
        } else {
            System.out.println( "Usage: corvarjartest <filename.res>" );
        }
    }
}
