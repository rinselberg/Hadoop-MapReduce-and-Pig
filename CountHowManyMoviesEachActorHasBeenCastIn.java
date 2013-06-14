package ucsc.hadoop.homework2;

import java.io.IOException;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import ucsc.hadoop.util.ConfigurationUtil;

/**
 * MapReduce application to count how many movies each actor has been cast in.
 * 
 * The output is sorted in descending order, using the count of movies for each actor as the sort key.
 * 
 *********************************************************************************************************** 
 * Use my saved Java launch configuration to run this application:                                         * 
 * ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/CountHowManyMoviesEachActorHasBeenCastIn.launch     * 
 *                                                                                                         * 
 * The output of the first MapReduce job goes to:                                                          * 
 * ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/output/PartTwo/part-r-00000                         * 
 *                                                                                                         *  
 * The output of the second MapReduce job goes to:                                                         * 
 * ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/output/PartTwo/job2/part-r-00000                    * 
 *                                                                                                         *  
 * The next to last step copies the output of the second MapReduce job to:                                 * 
 * ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/OutputDataForPartTwo                                *
 *                                                                                                         *  
 * The last step deletes the intermediate data that was generated by the MapReduce jobs.                   *
 *                                                                                                         * 
 * After the application has run to completion, access the output data file by selecting                   *
 * RonaldInselbergHadoopAssignmentTwo and Refresh to make OutputDataForPartTwo visible in the              *
 * Project Explorer window. Open OutputDataForPartTwo with a text editor to see the output data.           *   
 *                                                                                                         * 
 ***********************************************************************************************************
 * 
 * I cascaded two Hadoop MapReducer jobs, using the output of the first job as the input to the second job.
 * 
 * The number of reducers for each MapReduce job is set to one (1). This produces a single file
 * that has all of the transformed output data from job #1; an intermediate result that becomes the input
 * to job #2, which (because job #2 is also constrained to a single reducer) also produces a
 * single output file with all of the data as the final transformation.
 * 
 * The first MapReduce job produces the count of movies that each actor was cast in. Each record consists of
 * the count (number), followed by the unique actor name. The records are sorted by actor name, in ascending
 * lexicographic order. The mapper class for job #1 emits a key-value pair consisting of
 * <actorName, 1> for however many times the same actor name is encountered in the input database (imdb.tsv).
 * The Hadoop MapReduce framework processes the key-value pairs from all of the mappers, transforming
 * all key-value pairs with the same actor name into a sequence of key-value pairs
 * <uniqueActorName, 1 (,1)*> that is sorted by the framework on the key (uniqueActorName) in ascending
 * lexicographic order. The reducer for job #1 performs the counting operation for each unique actor name
 * and emits a sequence of <N, uniqueActorName> pairs where N is derived by summing of the value 1 (,1)*
 * paired with each unique actor name. As the input is passed to the reducer already sorted (by the framework)
 * on unique actor name in ascending lexicographic order, the output from job #1 preserves that same sorting order.
 * 
 * The output from job #1 is used as the input to job #2, which sorts the <N, uniqueActorName> pairs on
 * the key N (count of movies that each actor was cast in). A custom comparator (modified Apache code) is provided
 * to override the default sorting order and sort by decreasing numerical order, as specified for the assignment.
 * 
 * The program has only been tested in Hadoop local mode.
 *
 * CountHowManyMoviesEachActorHasBeenCastIn requires a single Java launch configuration argument to specify the
 * movies database input file:
 * 
 *     ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/data/movie/imdb.tsv
 * 
 * The final output data is copied and saved in:
 * 
 *     ${workspace_loc:RonaldInselbergHadoopAssignmentTwo}/OutputDataForPartTwo
 * 
 * 
 * @author Ronald Inselberg
 *
 */
public class CountHowManyMoviesEachActorHasBeenCastIn extends Configured implements Tool {
  
	private static final Log LOG = LogFactory.getLog(CountHowManyMoviesEachActorHasBeenCastIn.class);
	
	/*
	 * Designate two Hadoop MapReduce data directories. The second directory is subordinate to the
	 * first directory, so that both are deleted by a single call to delete the first directory. 
	 */
	private static final String job1OutputDirectoryName = "output/PartTwo";
	private static final String job2OutputDirectoryName = job1OutputDirectoryName + "/job2";
	
	/*
	 * Designate a destination file for the final output data.
	 * The file will either be created, or overwritten if it already exists.
	 */
	private static final String finalOutputDestinationFileName = "OutputDataForPartTwo";
	
	public int run(String[] args) throws Exception {
		Configuration conf = getConf();
		if (args.length != 1) {
			System.err.println("Usage: CountHowManyMoviesEachActorHasBeenCastIn <input>");
			System.exit(2);
		}
		
		/**
		 * Deletes Hadoop MapReduce data directory if it already exists.
		 * Otherwise Hadoop fails with a file system exception.
		 */
		File outputDirectory = new File("job1OutputDirectoryName");
		if (outputDirectory.exists()) {
			try {
				FileUtils.deleteDirectory(outputDirectory);
			} catch (IOException e) {
				System.err.println(e);
				throw new IllegalArgumentException("Unable to delete " + outputDirectory + " Is it write protected?");
			}
		}
		
		if (outputDirectory.exists()) {
			System.out.println("This should never happen, but if it happens anyway, please select /RonaldInselbergHadoopAssignmentTwo/output/PartTwo");
			System.out.println("in Project Explorer and Delete, then select CountHowManyMoviesEachActorHasBeenCastIn.launch and run again.");
			System.exit(99);
		}

		ConfigurationUtil.dumpConfigurations(conf, System.out);
		LOG.info("input: " + args[0] + " output: " + job1OutputDirectoryName);
		
		/**
		 * Configure MapReduce job #1
		 */
		Job job1 = new Job(conf, "MapReduce job1");
		job1.setJarByClass(CountHowManyMoviesEachActorHasBeenCastIn.class);
		job1.setMapperClass(MovieTokenizerMapper.class);
		job1.setReducerClass(ActorsListReducer.class);
		job1.setNumReduceTasks(1);		//write a single output file with all of the transformed data
		job1.setMapOutputKeyClass(Text.class);
		job1.setMapOutputValueClass(IntWritable.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);
		FileInputFormat.addInputPath(job1, new Path(args[0]));	//args[0] specifies the input data file, movie data in "tsv" format
		FileOutputFormat.setOutputPath(job1, new Path(job1OutputDirectoryName));		
		boolean resultFromJob1 = job1.waitForCompletion(true);
		
		/** Configure MapReduce job #2
		 * 
		 */
		Job job2 = new Job(conf, "MapReduce job2");
		job2.setJarByClass(CountHowManyMoviesEachActorHasBeenCastIn.class);
		job2.setMapperClass(CountHowManyMoviesEachActorHasBeenCastInMapper.class);
		job2.setReducerClass(DescendingKeysReducer.class);
		job2.setNumReduceTasks(1);		//write a single output file with all of the transformed data
		job2.setMapOutputKeyClass(IntWritable.class);
		job2.setMapOutputValueClass(Text.class);
		job2.setOutputKeyClass(IntWritable.class);
		job2.setOutputValueClass(Text.class);
		/*
		 * Override the default comparator that controls how the keys are sorted before they are passed to the Reducer.
		 * IntWritable keys <numberOfMovies> are sorted in decreasing (instead of increasing) numeric order
		 * using a custom comparator class.
		 */
		job2.setSortComparatorClass(DecreasingIntComparator.class);
		/*
		 * Output from MapReduce job #1 is used as input to MapReduce job #2
		 */
		FileInputFormat.addInputPath(job2, new Path(job1OutputDirectoryName + "/part-r-00000"));
		/*
		 * 
		 */
		FileOutputFormat.setOutputPath(job2, new Path(job2OutputDirectoryName));		
		boolean resultFromJob2 = job2.waitForCompletion(true);
		
		/**
		 * return 0 only if both MapReduce jobs (job #1 and job #2) are successful
		 */
		boolean result = resultFromJob1 && resultFromJob2;
		return (result) ? 0 : 1;
	}
	
	public static void main(String[] args) throws Exception {
		int exitCode = ToolRunner.run(new CountHowManyMoviesEachActorHasBeenCastIn(), args);
		
		// copy output data from second MapReduce job to final output destination file
		File finalOutputDestination = new File(finalOutputDestinationFileName);
		if (exitCode==0) {
			try {
				File outputFromMapReduceJob2 = new File(job2OutputDirectoryName + "/part-r-00000");
				FileUtils.copyFile(outputFromMapReduceJob2, finalOutputDestination);
			} catch (IOException e) {
				System.err.println(e);
				System.err.println("IOexception, unable to copy Job #2 output data to final output destination file");
				System.exit(1);
			}
		}
		System.out.println("output file is " + finalOutputDestination);
		System.out.println("select RonaldInselbergHadoopAssignmentTwo and Refresh to display " + finalOutputDestination + " in Project Explorer");
		System.out.println("open " + finalOutputDestination + " with a text editor to see the output data");
		
		// delete MapReduce diretories and data files
		File outputDirectory = new File(job1OutputDirectoryName);
		if (outputDirectory.exists()) {
			try {
				FileUtils.deleteDirectory(outputDirectory);
			} catch (IOException e) {
				System.err.println(e);
				System.err.println("unable to delete MapReduce data directories and files created by Hadoop");
				System.exit(1);
			}
		}
		// clean up and exit
		System.out.println("main has completed with exitCode " + exitCode);
		System.exit(exitCode);
	}
	
	/**
	 * 
	 * Mapper (job #1) processes the raw input data and emits a key-value pair of
	 * <actorName, 1> for every valid input record.
	 * 
	 * After processing by the MapReduce framework, the data is sorted on
	 * actorName in (default) ascending lexicographic order before
	 * it is passed as <uniqueActorName, 1 (,1)*> pairs as input to the reducer for job #1.
	 * 
	 */
	public static class MovieTokenizerMapper extends Mapper<Object, Text, Text, IntWritable> {
		private final static IntWritable ONE = new IntWritable(1);
		private static Text actorName = new Text();

		@Override
		public void map(Object key, Text value, Context context) 
				throws IOException, InterruptedException {
			String[] tokens = value.toString().split("\\t");
			if (tokens.length == 3) {
				actorName.set(tokens[0]);
				context.write(actorName, ONE);
			}
		}
	}
	
	/**
	 * 
	 * Reducer (job #1) computes the number of movies that each actor has been cast in
	 * and emits <numberOfMovies, uniqueActorName> pairs
	 *
	 */
	public static class ActorsListReducer extends Reducer<Text, IntWritable, IntWritable, Text> {
		private static IntWritable numberOfMovies = new IntWritable();
		
		@Override
		public void reduce(Text uniqueActorName, Iterable<IntWritable> values, Context context) 
				 throws IOException, InterruptedException {
			
			int movieCountPerActor = 0;
			for (IntWritable count : values) {
				movieCountPerActor += count.get();
			}
			numberOfMovies.set(movieCountPerActor);
			context.write(numberOfMovies, uniqueActorName);
		}
	}
	
	/**
	 * Mapper (job #2)
	 * 
	 * Similar to the IdentityMapper, does not transform the data
	 */
	public static class CountHowManyMoviesEachActorHasBeenCastInMapper extends Mapper<Object, Text, IntWritable, Text> {
		private static IntWritable numberOfMovies = new IntWritable();
		private static Text uniqueActorName = new Text();

		@Override
		public void map(Object key, Text value, Context context) 
				throws IOException, InterruptedException {
			String[] tokens = value.toString().split("\\t");
			if (tokens.length == 2) {
				numberOfMovies.set(Integer.parseInt(tokens[0]));
				uniqueActorName.set(tokens[1]);
				context.write(numberOfMovies, uniqueActorName);
			} else {
				System.err.println("Job #2, Mapper, encountered unexpected input data");
				System.exit(1);
				
			}
		}
	}
	
	/** 
	 * Reducer (job #2) checks whether the keys are received in descending order
	 * 
	 * I copied this code from the Apache repository at https://svn.apache.org..
	 * and modified it for this application.
	 */
	public static class DescendingKeysReducer
	extends Reducer<IntWritable, Text, IntWritable, Text> {

		// keeps track of the last key that was processed before the current key as the data is received
		private static int lastKey = Integer.MAX_VALUE;
		
		public void reduce(IntWritable key, Text value, 
				OutputCollector<IntWritable, Text> out,
				Reporter reporter) throws IOException {
			int currentKey = key.get();
			// keys should be in descending order
			if (currentKey > lastKey) {
				System.err.println("Job #2, Reducer, keys are not sorted in descending order");
				System.exit(1);

			}
			lastKey = currentKey;
			out.collect(key, new Text("success"));
		}
	}
	
	/** 
	 * A decreasing Comparator for IntWritable, to sort IntWritable keys in descending order (job #2)
	 * 
	 * I copied this code from the Apache repository at https://svn.apache.org..
	 */ 
	public static class DecreasingIntComparator extends IntWritable.Comparator {
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return -super.compare(b1, s1, l1, b2, s2, l2);
		}
		static {                    // register this comparator
			WritableComparator.define(DecreasingIntComparator.class,
					new IntWritable.Comparator());
		}
	}

}
