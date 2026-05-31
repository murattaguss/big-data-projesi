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
import java.util.HashMap;
import java.util.Map;

/**
 * MapReduce Job 2: Normalizes term frequencies by dividing each term's count by
 * the total number of words in the corresponding document.
 * Input: word@docId \t count (from Job 1 output)
 * Output: word \t docId=tf
 */
public class DocWordCountJob {

    public static class DocWordCountMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // Raw line format: word@docId \t count
            String line = value.toString();
            String[] parts = line.split("\t");
            if (parts.length < 2) {
                return;
            }

            String wordAndDoc = parts[0];
            String count = parts[1];

            int splitIdx = wordAndDoc.lastIndexOf('@');
            if (splitIdx != -1) {
                String word = wordAndDoc.substring(0, splitIdx);
                String docId = wordAndDoc.substring(splitIdx + 1);

                outKey.set(docId);
                outValue.set(word + "=" + count);
                context.write(outKey, outValue);
            }
        }
    }

    public static class DocWordCountReducer extends Reducer<Text, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        public void reduce(Text docId, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int docLen = 0;
            Map<String, Integer> wordCounts = new HashMap<>();

            // Cache values in memory to calculate total document length first
            // Note: Since Hadoop reuses Text objects, we must convert/copy to String
            for (Text val : values) {
                String valStr = val.toString();
                int eqIdx = valStr.lastIndexOf('=');
                if (eqIdx != -1) {
                    String word = valStr.substring(0, eqIdx);
                    try {
                        int count = Integer.parseInt(valStr.substring(eqIdx + 1));
                        wordCounts.put(word, count);
                        docLen += count;
                    } catch (NumberFormatException e) {
                        // Skip malformed entries
                    }
                }
            }

            if (docLen == 0) {
                return;
            }

            // Emit the normalized term frequency (tf) for each word
            for (Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
                String word = entry.getKey();
                int count = entry.getValue();
                double tf = (double) count / docLen;

                outKey.set(word);
                outValue.set(docId.toString() + "=" + tf);
                context.write(outKey, outValue);
            }
        }
    }

    public static Job createJob(Configuration conf, Path inputPath, Path outputPath) throws IOException {
        Job job = Job.getInstance(conf, "Document Word Count Normalization (Job 2)");
        job.setJarByClass(DocWordCountJob.class);

        job.setMapperClass(DocWordCountMapper.class);
        job.setReducerClass(DocWordCountReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);

        return job;
    }
}
