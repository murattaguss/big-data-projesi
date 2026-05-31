package com.bdata;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MapReduce Job 3: Calculates the final TF-IDF scores for each word in each document.
 * Input: word \t docId=tf (from Job 2 output)
 * Output: word \t docId1:tfidf1,docId2:tfidf2,... (Final Inverted Index)
 */
public class TFIDFJob {

    public static class TFIDFMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // Raw line format: word \t docId=tf
            String line = value.toString();
            String[] parts = line.split("\t");
            if (parts.length < 2) {
                return;
            }

            outKey.set(parts[0]);
            outValue.set(parts[1]);
            context.write(outKey, outValue);
        }
    }

    public static class TFIDFReducer extends Reducer<Text, Text, Text, Text> {
        private final Text outValue = new Text();
        private int totalDocs = 100; // Default fallback

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            // Read the total number of documents passed from the driver configuration
            totalDocs = context.getConfiguration().getInt("total.documents", 100);
        }

        @Override
        public void reduce(Text word, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            List<String> cachedValues = new ArrayList<>();

            // Cache values to count Document Frequency (DF)
            for (Text val : values) {
                cachedValues.add(val.toString());
            }

            int df = cachedValues.size();
            if (df == 0) {
                return;
            }

            // Calculate IDF using natural log: ln(N / DF)
            // Ensuring the division is safe and IDF is non-negative
            double idf = Math.log((double) totalDocs / df);
            if (idf < 0) {
                idf = 0.0;
            }

            StringBuilder indexEntry = new StringBuilder();

            for (int i = 0; i < cachedValues.size(); i++) {
                String valStr = cachedValues.get(i);
                int eqIdx = valStr.lastIndexOf('=');
                if (eqIdx != -1) {
                    String docId = valStr.substring(0, eqIdx);
                    try {
                        double tf = Double.parseDouble(valStr.substring(eqIdx + 1));
                        double tfidf = tf * idf;

                        if (indexEntry.length() > 0) {
                            indexEntry.append(",");
                        }
                        indexEntry.append(docId).append(":").append(tfidf);
                    } catch (NumberFormatException e) {
                        // Skip malformed entries
                    }
                }
            }

            if (indexEntry.length() > 0) {
                outValue.set(indexEntry.toString());
                context.write(word, outValue);
            }
        }
    }

    public static Job createJob(Configuration conf, Path inputPath, Path outputPath) throws IOException {
        Job job = Job.getInstance(conf, "TF-IDF Scoring & Inverted Index (Job 3)");
        job.setJarByClass(TFIDFJob.class);

        job.setMapperClass(TFIDFMapper.class);
        job.setReducerClass(TFIDFReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);

        return job;
    }
}
