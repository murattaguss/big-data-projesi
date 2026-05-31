package com.bdata;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * MapReduce Job 1: Calculates Term Frequency (TF) of each word in each document.
 * Input: Raw text documents.
 * Output: word@docId \t count
 */
public class TermFrequencyJob {

    public static class TFMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private final static IntWritable ONE = new IntWritable(1);
        private final Text wordDocKey = new Text();
        private String docId;
        private static final Pattern WORD_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            // Retrieve document name from the split details
            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            String fileName = fileSplit.getPath().getName();
            // Clean up the extension to get the integer doc ID
            if (fileName.endsWith(".txt")) {
                docId = fileName.substring(0, fileName.length() - 4);
            } else {
                docId = fileName;
            }
        }

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString().toLowerCase(java.util.Locale.ROOT);
            String[] words = WORD_PATTERN.split(line);
            
            for (String word : words) {
                word = word.trim();
                if (!word.isEmpty()) {
                    wordDocKey.set(word + "@" + docId);
                    context.write(wordDocKey, ONE);
                }
            }
        }
    }

    public static class TFReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable result = new IntWritable();

        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }

    public static Job createJob(Configuration conf, Path inputPath, Path outputPath) throws IOException {
        Job job = Job.getInstance(conf, "Term Frequency (Job 1)");
        job.setJarByClass(TermFrequencyJob.class);

        job.setMapperClass(TFMapper.class);
        job.setCombinerClass(TFReducer.class);
        job.setReducerClass(TFReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        org.apache.hadoop.fs.FileSystem fs = org.apache.hadoop.fs.FileSystem.get(conf);
        int limit = conf.getInt("document.limit", Integer.MAX_VALUE);
        String baseBooksPathStr = conf.get("base.books.path", "/input/all_books");
        Path baseBooksPath = new Path(baseBooksPathStr);

        if (limit != Integer.MAX_VALUE && fs.exists(baseBooksPath)) {
            org.apache.hadoop.fs.FileStatus[] fileStatuses = fs.listStatus(baseBooksPath);
            int addedCount = 0;
            for (org.apache.hadoop.fs.FileStatus status : fileStatuses) {
                if (status.isFile() && status.getPath().getName().endsWith(".txt")) {
                    FileInputFormat.addInputPath(job, status.getPath());
                    addedCount++;
                    if (addedCount >= limit) {
                        break;
                    }
                }
            }
            System.out.println("Added " + addedCount + " files to MapReduce Job 1 input.");
        } else {
            FileInputFormat.addInputPath(job, inputPath);
        }

        FileOutputFormat.setOutputPath(job, outputPath);

        return job;
    }
}
