package com.bdata;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;

/**
 * Pipeline Runner: Chains MapReduce Job 1 (Term Frequency), Job 2 (Normalization),
 * and Job 3 (TF-IDF Inverted Index Scoring) sequentially.
 * Dynamically counts the number of files in the input directory to determine N.
 * Cleans up any existing output directories before starting.
 */
public class JobPipeline {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: JobPipeline <input_path> <output_base_path> [job_type: all|job1|job2|job3]");
            System.exit(1);
        }

        Path inputPath = new Path(args[0]);
        Path outputBasePath = new Path(args[1]);
        String jobType = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "all";

        Path job1OutputPath = new Path(outputBasePath, "tokens");
        Path job2OutputPath = new Path(outputBasePath, "index");
        Path job3OutputPath = new Path(outputBasePath, "tfidf");

        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);

        // Determine dataset limit based on inputPath name
        int limit = Integer.MAX_VALUE;
        String name = inputPath.getName();
        if (name.equals("small_books")) {
            limit = 100;
        } else if (name.equals("half_books")) {
            limit = 4000;
        }

        // Base books folder in HDFS
        Path baseBooksPath;
        if (name.equals("small_books") || name.equals("half_books")) {
            baseBooksPath = new Path("/input/all_books");
            if (!fs.exists(baseBooksPath)) {
                baseBooksPath = new Path("/input/books");
            }
            if (!fs.exists(baseBooksPath)) {
                baseBooksPath = inputPath;
            }
        } else {
            baseBooksPath = inputPath;
        }

        int numDocs = 0;
        if (fs.exists(baseBooksPath)) {
            FileStatus[] fileStatuses = fs.listStatus(baseBooksPath);
            int txtCount = 0;
            for (FileStatus status : fileStatuses) {
                if (status.isFile() && status.getPath().getName().endsWith(".txt")) {
                    txtCount++;
                }
            }
            numDocs = Math.min(limit, txtCount);
        }

        if (numDocs == 0) {
            numDocs = limit == 100 ? 100 : (limit == 4000 ? 4000 : 8552); // fallback
        }

        System.out.println("=================================================");
        System.out.println("Dynamic document count (N): " + numDocs);
        System.out.println("=================================================");
        conf.setInt("total.documents", numDocs);
        conf.setInt("document.limit", limit);
        conf.set("base.books.path", baseBooksPath.toString());

        if (jobType.equals("all") || jobType.equals("job1")) {
            if (fs.exists(job1OutputPath)) {
                System.out.println("Cleaning Job 1 output path: " + job1OutputPath);
                fs.delete(job1OutputPath, true);
            }
            System.out.println("Starting MapReduce Job 1 (Tokenization)...");
            Job job1 = TermFrequencyJob.createJob(conf, inputPath, job1OutputPath);
            if (!job1.waitForCompletion(true)) {
                System.err.println("Job 1 failed! Aborting.");
                System.exit(1);
            }
            System.out.println("Job 1 completed successfully.");
        }

        if (jobType.equals("all") || jobType.equals("job2")) {
            if (fs.exists(job2OutputPath)) {
                System.out.println("Cleaning Job 2 output path: " + job2OutputPath);
                fs.delete(job2OutputPath, true);
            }
            System.out.println("Starting MapReduce Job 2 (Inverted Index Construction)...");
            Path job2Input = jobType.equals("job2") ? inputPath : job1OutputPath;
            Job job2 = DocWordCountJob.createJob(conf, job2Input, job2OutputPath);
            if (!job2.waitForCompletion(true)) {
                System.err.println("Job 2 failed! Aborting.");
                System.exit(1);
            }
            System.out.println("Job 2 completed successfully.");
        }

        if (jobType.equals("all") || jobType.equals("job3")) {
            if (fs.exists(job3OutputPath)) {
                System.out.println("Cleaning Job 3 output path: " + job3OutputPath);
                fs.delete(job3OutputPath, true);
            }
            System.out.println("Starting MapReduce Job 3 (TF-IDF Scoring)...");
            Path job3Input = jobType.equals("job3") ? inputPath : job2OutputPath;
            Job job3 = TFIDFJob.createJob(conf, job3Input, job3OutputPath);
            if (!job3.waitForCompletion(true)) {
                System.err.println("Job 3 failed! Aborting.");
                System.exit(1);
            }
            System.out.println("Job 3 completed successfully.");
            System.out.println("MapReduce pipeline execution finished successfully! Final index generated at: " + job3OutputPath);
        }
    }
}
