/**
 *  Copyright (c) 2016 Intel Corporation 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tensorflow

import org.apache.commons.lang.StringUtils
import org.apache.hadoop.io.{ BytesWritable, NullWritable }
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.tensorflow.example.Example
import org.tensorflow.hadoop.io.TFRecordFileInputFormat
import org.tensorflow.serde.DefaultTfRecordRowDecoder
import org.apache.spark.sql.types._

object ImportTensorflow {
  /**
   * Creates a DataFrame using TensorFlow Records path with specified schema
   *
   * TensorFlow records are the standard data format for TensorFlow. The recommended format for TensorFlow is a TFRecords file
   * containing tf.train.Example protocol buffers. The tf.train.Example protocol buffers encodes (which contain Features as a field).
   * https://www.tensorflow.org/how_tos/reading_data
   *
   * During Import, the API parses TensorFlow DataTypes as below:
   *
   * Int64List => IntegerType or LongType
   * FloatList => FloatType or DoubleType
   * Any other DataType (Ex: String) => BytesList
   *
   * @param sparkSession sparkSession
   * @param sourceTfRecordsPath Full path to TensorFlow records on HDFS/Local filesystem
   * @param schema Optional DataFrame schema to use during import. If not defined, then the schema is inferred from the TensorFlow records
   * @return DataFrame with data from TensorFlow records
   */
  def importTensorflow(sparkSession: SparkSession, sourceTfRecordsPath: String, schema: Option[StructType] = None): DataFrame = {
    require(StringUtils.isNotEmpty(sourceTfRecordsPath), "path should not be null or empty.")

    val rdd = sparkSession.sparkContext.newAPIHadoopFile(sourceTfRecordsPath, classOf[TFRecordFileInputFormat], classOf[BytesWritable], classOf[NullWritable])

    val exampleRdd = rdd.map {
      case (bytesWritable, nullWritable) => Example.parseFrom(bytesWritable.getBytes)
    }

    val finalSchema = schema.getOrElse(TensorflowInferSchema(exampleRdd))

    val resultRdd = exampleRdd.map(example => DefaultTfRecordRowDecoder.decodeTfRecord(example, finalSchema))

    sparkSession.createDataFrame(resultRdd, finalSchema)
  }

}
