import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import java.io.IOException;
import java.util.Comparator;
import java.util.TreeMap;

public class Sort {
    public static class Map extends Mapper<Object, Text, NullWritable, Text>{

        /**
         * TreeMap keeps items sorted according to the comparator passed to it
         * Comparator's natural order is used to take care of duplicate elements
         */
        private TreeMap<Double, Text> prMap = new TreeMap<>(Comparator.naturalOrder());

        public void map(Object key, Text value, Context context
        ) throws IOException, InterruptedException {
            String line = value.toString();
            String[] kvPair = line.split("\t");
            String k   = kvPair[0];
            String pr = kvPair[kvPair.length-1];
            prMap.put(Double.parseDouble(pr), new Text(k + "\t" + pr));
            if (prMap.size() > 100)
                prMap.remove(prMap.firstKey());
        }

        @Override
        protected void cleanup(Context context) throws IOException,
                InterruptedException {
            for (Text t : prMap.values()) {
                // Since we're only emitting 100 elements, sending all to one reducer is fine
                context.write(NullWritable.get(), t);
            }
        }
    }

    public static class Reduce extends Reducer<NullWritable,Text,Text,Text> {

        /**
         * TreeMap keeps items sorted according to the comparator passed to it
         * Comparator's natural order is used to take care of duplicate elements
         */
        private TreeMap<Double, Text> prMap = new TreeMap<>(Comparator.naturalOrder());

        @Override
        public void reduce(NullWritable key, Iterable<Text> values,
                           Context context) throws IOException, InterruptedException {
            for (Text value : values) {
                String[] parsed = value.toString().split("\t");
                prMap.put(Double.parseDouble(parsed[1]), new Text(parsed[0]));
                if (prMap.size() > 100) {
                    prMap.remove(prMap.firstKey());
                }
            }

            for (java.util.Map.Entry<Double, Text> t : prMap.descendingMap()
                    .entrySet()) {
                context.write(t.getValue(), new Text(t.getKey().toString()));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("hadoop.home.dir", "/home/daniel/hadoop");
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 2) {
            System.err.println("Usage: PageRank <in> <out>");
            System.exit(1);
        }
        Path inPath = new Path(otherArgs[0]);
        Path outPath = new Path(otherArgs[1]);
        Job job = Job.getInstance(conf);
        job.setJarByClass(Sort.class);
        job.setMapperClass(Map.class);
        job.setReducerClass(Reduce.class);
        job.setNumReduceTasks(1);
        job.setMapOutputKeyClass(NullWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.setInputPaths(job, inPath);
        FileOutputFormat.setOutputPath(job, outPath);
        outPath.getFileSystem(conf).delete(outPath, true);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}