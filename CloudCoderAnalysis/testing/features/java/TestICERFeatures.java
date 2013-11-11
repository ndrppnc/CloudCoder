package junit.org.cloudcoder.analysis.features.java;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.cloudcoder.analysis.features.java.Analyze;
import org.cloudcoder.analysis.features.java.Feature;
import org.cloudcoder.analysis.features.java.FeatureVisitor;
import org.cloudcoder.analysis.features.java.MyConnection;
import org.junit.Test;

import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry.Entry;

public class TestICERFeatures
{
    private static String folder="testing/features/java";
    private static String load(String filename) 
    throws IOException
    {
        if (!filename.endsWith(".java")) {
            filename+=".java";
        }
        String res="";
        Scanner scan=new Scanner(new FileInputStream(folder+"/"+filename));
        while (scan.hasNext()) {
            res+=scan.nextLine()+"\n";
        }
        return res;
    }
    
    private static void test(String filename) throws Exception {
        TestDotfile.dotFile(filename);
        FeatureVisitor v=new FeatureVisitor();
        HashMap<Feature, Integer> map=v.extractFeatures(load(filename));
        for (Map.Entry<Feature,Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() +" => "+entry.getValue());
        }
    }
    
    @Test
    public void testIf1() throws Exception {
        test("If1");
    }
    
    @Test
    public void testIfElse1() throws Exception {
        test("IfElse1");
    }
    
    @Test
    public void testIfElseIfElse() throws Exception {
        test("IfElseIfElse");
    }
    
    @Test
    public void testIfElseIf() throws Exception {
        test("IfElseIf");
    }
    
    @Test
    public void testIfChain1() throws Exception {
        test("IfChain1");
    }
    
    @Test
    public void testIfChain2() throws Exception {
        test("IfChain2");
    }
    
    @Test
    public void testManyReturns() throws Exception {
        test("ManyReturns");
    }

}
