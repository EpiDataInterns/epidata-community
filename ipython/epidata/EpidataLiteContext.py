#from data_frame import DataFrame

from datetime import datetime
import _private.py4j_additions
import json
import os
import signal
from py4j.java_collections import ListConverter, MapConverter
import re
import time
import urllib
from threading import Thread
#from streaming import EpidataStreamingContext
from _private.transformation import Transformation
from py4j.java_gateway import JavaGateway
import pandas as pd
import py4j


class EpidataLiteContext:
    def __init__(self):

        # self.gateway = JavaGateway()

        gateway = JavaGateway()

        #other confs and connections

        #works with an absolute path as well

        # gg = self.gateway.launch_gateway(classpath="../../spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")
        # gg = self.gateway.launch_gateway(classpath="spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")

        gg = gateway.launch_gateway(classpath="spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")

        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 

        
    def to_pandas_dataframe(self, list_of_dicts):
        pdf = pd.DataFrame.from_records(list_of_dicts) 
        return pdf
   
    """
    Query methods for epidata measurements

    Parameters
    ----------
    field_query : dictionary containing either strings or lists of strings
        A dictionary containing field names and the values those fields must
        contain in matching measurements. Some system configurations require
        that values of specific fields be specified. A string field value
        represents an equality match, while a list value represents set
        membership (all values within the set are matched).
    begin_time : datetime
        Beginning of the time interval to query, inclusive.
    end_time : datetime
        End of the time interval to query, exclusive.

    Returns
    -------
    A dataframe (pandas? Epidata?) containing measurements matching the query
    """
    
    def query_measurements_original(self, field_query, begin_time, end_time):
        params = py4j.java_gateway.GatewayParameters(auto_convert=True)
        gateway = JavaGateway(gateway_parameters=params)
        gg = gateway.launch_gateway(classpath="spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 

        #java_field_query, java_begin_time, java_end_time = field_query, begin_time, end_time
        java_field_query, java_begin_time, java_end_time = self._to_java_params(field_query, begin_time, end_time)
        java_df = java_entry.query(java_field_query, java_begin_time, java_end_time)
        pdf = self.to_pandas_dataframe(java_df)
        return pdf
        

    def query_measurements_cleansed(self, field_query, begin_time, end_time):
        params = py4j.java_gateway.GatewayParameters(auto_convert=True)
        gateway = JavaGateway(gateway_parameters=params)
        gg = gateway.launch_gateway(classpath="./spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar") 
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 

        java_field_query, java_begin_time, java_end_time = self._to_java_params(field_query, begin_time, end_time)
        java_df = self.java_entry.queryMeasurementCleansed(java_field_query, java_begin_time, java_end_time)
        pdf = self.to_pandas_dataframe(java_df)
        return pdf
 
    def query_measurements_summary(self, field_query, begin_time, end_time):
        params = py4j.java_gateway.GatewayParameters(auto_convert=True)
        gateway = JavaGateway(gateway_parameters=params)
        gg = gateway.launch_gateway(classpath="./spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar") 
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 

        java_field_query, java_begin_time, java_end_time = self._to_java_params(field_query, begin_time, end_time)
        java_df = self.java_entry.queryMeasurementSummary(java_field_query, java_begin_time, java_end_time)
        pdf = self.to_pandas_dataframe(java_df)
        return pdf

    def list_keys(self):
        params = py4j.java_gateway.GatewayParameters(auto_convert=True)
        gateway = JavaGateway(gateway_parameters=params)
        gg = gateway.launch_gateway(classpath="./spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar") 
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 
        """
        List the epidata measurement keys.

        Returns
        -------
        result : epidata DataFrame
            A DataFrame containing values of the principal fields used for
            classifying measurements.
        """
        java_df = self.java_entry.listKeys() 
        return self.to_pandas_dataframe(java_df) #does/should this return pandas dataframe or epidata dataframe? 

    def _to_java_params(self, field_query, begin_time, end_time):

        #https://www.py4j.org/advanced_topics.html 

        gateway = JavaGateway()
        gg = gateway.launch_gateway(classpath="spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 
        gc = gg._gateway_client
 
        def to_java_list(x):
            if isinstance(x, basestring): #or str
                return ListConverter().convert([x], gc)
            return ListConverter().convert(x, gc)
        

        java_list_field_query = {k: to_java_list(v) for k, v in field_query.items()}
        java_field_query = MapConverter().convert(field_query, gc)
        java_begin_time = self._to_java_timestamp(begin_time)
        java_end_time = self._to_java_timestamp(end_time)
        return field_query, java_begin_time, java_end_time


    def _to_java_timestamp(self, dt):
        gg = JavaGateway().launch_gateway(classpath="spark/target/scala-2.12/epidata-spark-assembly-1.0-SNAPSHOT.jar")
        java_entry = gg.jvm.com.epidata.spark.EpidataLiteContext() 
        stamp = time.mktime(dt.timetuple()) * 1e3 + dt.microsecond / 1e3
        timestamp = int(float(stamp))
        return gg.jvm.java.sql.Timestamp(timestamp)

    def _check_cluster_memory(self):
        pass  #not needed with the lite version?
        
    #streaming, transformation methods as needed 


'''
testing code to see if it compiles


from datetime import datetime, timedelta
ec = EpidataLiteContext() 
print(ec.to_pandas_dataframe([ {"hi": "hi"}, {"two": "three"}]))

ts = [datetime.fromtimestamp(1428004316.123 + x) for x in range(6)]
result = ec.query_measurements_original({'company': 'Company-1', 'site': 'Site-1','device_group': '1000','tester': 'Station-1','test_name': 'Test-1'}, ts[0], ts[5])
print(result)
'''


