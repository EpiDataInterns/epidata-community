package com.epidata.spark

import com.epidata.spark.models.{ MeasureLite => BaseMeasurement, MeasureLiteCleansed => BaseMeasurementCleansed }
import com.epidata.spark.models.util.{ SQLiteTypeUtils, Binary }
import java.sql.Timestamp
import java.util.Date
import org.apache.spark.sql.Row

import org.apache.spark.MeasurementValue

/**
 * Model representing a customer measurement stored in the database. The minor
 * differences from BaseMeasurement allow for integration with Spark SQL.
 */
case class MeasurementLite(
    customer: String,
    customer_site: String,
    collection: String,
    dataset: String,
    ts: Timestamp,
    key1: Option[String],
    key2: Option[String],
    key3: Option[String],
    meas_datatype: Option[String],
    meas_value: MeasurementValue,
    meas_unit: Option[String],
    meas_status: Option[String],
    meas_lower_limit: Option[MeasurementValue],
    meas_upper_limit: Option[MeasurementValue],
    meas_description: Option[String],
    val1: Option[String],
    val2: Option[String])

object MeasurementLite {

  // Splitting timeseries by epoch keeps partitions from growing beyond
  // capacity. The epoch is computed directly from the timestamp.
  def epochForTs(ts: Timestamp): Int = SQLiteTypeUtils.epochForTs(new Date(ts.getTime))

  implicit def baseMeasurementToMeasurement(base: BaseMeasurement): MeasurementLite =
    MeasurementLite(
      base.customer,
      base.customer_site,
      base.collection,
      base.dataset,
      new Timestamp(base.ts.getTime),
      base.key1,
      base.key2,
      base.key3,
      base.meas_datatype,
      base.meas_value match {
        case b: Binary => MeasurementValue(b.backing)
        case v => MeasurementValue(v)
      },
      base.meas_unit,
      base.meas_status,
      base.meas_lower_limit.map(MeasurementValue(_)),
      base.meas_upper_limit.map(MeasurementValue(_)),
      base.meas_description,
      base.val1,
      base.val2)

  implicit def rowToMeasurement(row: Row): MeasurementLite = {
    baseMeasurementToMeasurement(row)
  }
}

/**
 * Model representing a measurement key stored in the database's
 * measurement_keys table. Each measurement key is a partition key value from
 * the database's measurements table.
 */
case class MeasurementLiteKey(
    customer: String,
    customer_site: String,
    collection: String,
    dataset: String)

case class MeasurementLiteCleansed(
    customer: String,
    customer_site: String,
    collection: String,
    dataset: String,
    ts: Timestamp,
    key1: Option[String],
    key2: Option[String],
    key3: Option[String],
    meas_datatype: Option[String],
    meas_value: MeasurementValue,
    meas_unit: Option[String],
    meas_status: Option[String],
    meas_flag: Option[String],
    meas_method: Option[String],
    meas_lower_limit: Option[MeasurementValue],
    meas_upper_limit: Option[MeasurementValue],
    meas_description: Option[String],
    val1: Option[String],
    val2: Option[String])

object MeasurementLiteCleansed {

  // Splitting timeseries by epoch keeps partitions from growing beyond
  // capacity. The epoch is computed directly from the timestamp.
  def epochForTs(ts: Timestamp): Int = SQLiteTypeUtils.epochForTs(new Date(ts.getTime))

  implicit def baseMeasurementCleansedToMeasurementCleansed(base: BaseMeasurementCleansed): MeasurementLiteCleansed =
    MeasurementLiteCleansed(
      base.customer,
      base.customer_site,
      base.collection,
      base.dataset,
      new Timestamp(base.ts.getTime),
      base.key1,
      base.key2,
      base.key3,
      base.meas_datatype,
      base.meas_value match {
        case b: Binary => MeasurementValue(b.backing)
        case v => MeasurementValue(v)
      },
      base.meas_unit,
      base.meas_status,
      base.meas_flag,
      base.meas_method,
      base.meas_lower_limit.map(MeasurementValue(_)),
      base.meas_upper_limit.map(MeasurementValue(_)),
      base.meas_description,
      base.val1,
      base.val2)

  implicit def rowToMeasurementCleansed(row: Row): MeasurementLiteCleansed =
    baseMeasurementCleansedToMeasurementCleansed(row)
}