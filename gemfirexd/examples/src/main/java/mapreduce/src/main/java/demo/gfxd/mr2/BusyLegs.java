/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package demo.gfxd.mr2;

import com.pivotal.gemfirexd.hadoop.mapreduce.RowInputFormat;
import com.pivotal.gemfirexd.internal.engine.GfxdDataSerializable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.reduce.IntSumReducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * This class finds the busiest legs between airports. The equivalent SQL
 * might be:
 *
 *   SELECT (CASE WHEN orig_airport < dest_airport THEN orig_airport||dest_airport ELSE dest_airport||orig_airport END), count(*) B
 *     FROM flights_history
 *     GROUP BY case when orig_airport < dest_airport THEN orig_airport||dest_airport ELSE dest_airport||orig_airport END
 *     ORDER BY B
 */
public class BusyLegs extends Configured implements Tool {

  public static class SampleMapper extends Mapper<Object, ResultSet, Text, IntWritable> {

    private final static IntWritable countOne = new IntWritable(1);
    private final Text reusableText = new Text();

    @Override
    public void map(Object key, ResultSet rs, Context context)
        throws IOException, InterruptedException {

      String origAirport;
      String destAirport;
      String combo;

      try {
          origAirport = rs.getString("ORIG_AIRPORT");
          destAirport = rs.getString("DEST_AIRPORT");
          combo = origAirport.compareTo(
              destAirport) < 0 ? origAirport + destAirport : destAirport + origAirport;
          reusableText.set(combo);
          context.write(reusableText, countOne);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  public int run(String[] args) throws Exception {

    GfxdDataSerializable.initTypes();
    Configuration conf = getConf();

    Path outputPath = new Path(args[0]);
    String hdfsHomeDir = args[1];
    String tableName = args[2];

    outputPath.getFileSystem(conf).delete(outputPath, true);

    conf.set(RowInputFormat.HOME_DIR, hdfsHomeDir);
    conf.set(RowInputFormat.INPUT_TABLE, tableName);
    conf.setBoolean(RowInputFormat.CHECKPOINT_MODE, false);

    Job job = Job.getInstance(conf, "Busy Leg Count");

    job.setInputFormatClass(RowInputFormat.class);

    // configure mapper and reducer
    job.setMapperClass(SampleMapper.class);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);

    // configure output
    TextOutputFormat.setOutputPath(job, outputPath);
    job.setOutputFormatClass(TextOutputFormat.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);

    return job.waitForCompletion(true) ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("SampleApp.main() invoked with " + Arrays.toString(args));
    int rc = ToolRunner.run(new BusyLegs(), args);
    System.exit(rc);
  }
}

