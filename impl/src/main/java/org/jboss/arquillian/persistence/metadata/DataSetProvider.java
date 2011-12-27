/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.persistence.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.arquillian.persistence.ShouldMatchDataSet;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.jboss.arquillian.persistence.configuration.PersistenceConfiguration;
import org.jboss.arquillian.persistence.data.DataSetDescriptor;
import org.jboss.arquillian.persistence.data.DataSetFileNamingStrategy;
import org.jboss.arquillian.persistence.data.ExpectedDataSetFileNamingStrategy;
import org.jboss.arquillian.persistence.data.Format;
import org.jboss.arquillian.persistence.exception.InvalidDataSetLocation;
import org.jboss.arquillian.persistence.exception.UnsupportedDataFormatException;
import org.jboss.arquillian.test.spi.TestClass;

/**
 *
 * @author <a href="mailto:bartosz.majsak@gmail.com">Bartosz Majsak</a>
 *
 */
public class DataSetProvider
{

   private final PersistenceConfiguration configuration;

   private final MetadataExtractor metadataExtractor;

   public DataSetProvider(MetadataExtractor metadataExtractor, PersistenceConfiguration configuration)
   {
      this.metadataExtractor = metadataExtractor;
      this.configuration = configuration;
   }

   /**
    * Returns all data sets defined for this test class
    * including those defined on the test method level.
    *
    * @param testClass
    * @return
    */
   public Set<DataSetDescriptor> getDataSetDescriptors(TestClass testClass)
   {
      final Set<DataSetDescriptor> dataSetDescriptors = new HashSet<DataSetDescriptor>();
      for (Method testMethod : testClass.getMethods(UsingDataSet.class))
      {
         dataSetDescriptors.addAll(getDataSetDescriptors(testMethod));
      }
      dataSetDescriptors.addAll(obtainClassLevelDataSet(testClass.getAnnotation(UsingDataSet.class)));
      return dataSetDescriptors ;
   }

   /**
    * Returns all expected data sets defined for this test class
    * including those defined on the test method level.
    *
    * @param testClass
    * @return
    */
   public Set<DataSetDescriptor> getExpectedDataSetDescriptors(TestClass testClass)
   {
      final Set<DataSetDescriptor> dataSetDescriptors = new HashSet<DataSetDescriptor>();
      for (Method testMethod : testClass.getMethods(ShouldMatchDataSet.class))
      {
         dataSetDescriptors.addAll(getExpectedDataSetDescriptors(testMethod));
      }
      dataSetDescriptors.addAll(obtainClassLevelDataSet(testClass.getAnnotation(ShouldMatchDataSet.class)));
      return dataSetDescriptors ;
   }

   public List<DataSetDescriptor> getDataSetDescriptors(Method testMethod)
   {
      final List<DataSetDescriptor> dataSetDescriptors = new ArrayList<DataSetDescriptor>();
      for (String dataFileName : getDataFileNames(testMethod))
      {
         DataSetDescriptor dataSetDescriptor = createDataSetDescriptor(dataFileName);
         dataSetDescriptors.add(dataSetDescriptor);
      }

      return dataSetDescriptors;
   }

   public List<DataSetDescriptor> getExpectedDataSetDescriptors(Method testMethod)
   {
      final List<DataSetDescriptor> dataSetDescriptors = new ArrayList<DataSetDescriptor>();
      for (String dataFileName : getExpectedDataFileNames(testMethod))
      {
         DataSetDescriptor dataSetDescriptor = createDataSetDescriptor(dataFileName);
         dataSetDescriptors.add(dataSetDescriptor);
      }

      return dataSetDescriptors;
   }

   private DataSetDescriptor createDataSetDescriptor(String dataFileName)
   {
      return new DataSetDescriptor(determineDataSetLocation(dataFileName), inferFormat(dataFileName));
   }

   private Format inferFormat(String dataFileName)
   {
      Format format = Format.inferFromFile(dataFileName);
      if (Format.UNSUPPORTED.equals(format))
      {
         throw new UnsupportedDataFormatException("File " + dataFileName + " is not supported.");
      }
      return format;
   }


   /**
    * Checks if data set file exists in the default location {@link PersistenceConfiguration#getDefaultDataSetLocation()}.
    * If that's not the case, file is looked up starting from the root.
    *
    * @return determined file location
    *
    * @throws
    *
    */
   private String determineDataSetLocation(String dataSetLocation)
   {
      if (existsInDefaultLocation(dataSetLocation))
      {
         return defaultDataSetFolder() + dataSetLocation;
      }
      if (!existsInGivenLocation(dataSetLocation))
      {
         throw new InvalidDataSetLocation("Unable to locate " + dataSetLocation +
               ". File does not exist even in default location " + configuration.getDefaultDataSetLocation());
      }
      return dataSetLocation;
   }

   private boolean existsInGivenLocation(String dataSetLocation)
   {
      try
      {
         final URL url = load(dataSetLocation);
         if (url == null)
         {
            return false;
         }
      }
      catch (URISyntaxException e)
      {
         throw new InvalidDataSetLocation("Unable to open data set file", e);
      }

      return true;
   }

   private boolean existsInDefaultLocation(String dataSetLocation)
   {
      String defaultDataSetLocation = defaultDataSetFolder() + dataSetLocation;
      return existsInGivenLocation(defaultDataSetLocation);
   }

   private URL load(String resourceLocation) throws URISyntaxException
   {
      return Thread.currentThread().getContextClassLoader().getResource(resourceLocation);
   }

   private String defaultDataSetFolder()
   {
      String defaultDataSetLocation = configuration.getDefaultDataSetLocation();
      if (!defaultDataSetLocation.endsWith("/"))
      {
         defaultDataSetLocation += "/";
      }
      return defaultDataSetLocation;
   }

   private List<DataSetDescriptor> obtainClassLevelDataSet(Annotation classLevelAnnotation)
   {
      if (classLevelAnnotation == null)
      {
         return Collections.emptyList();
      }

      final List<DataSetDescriptor> dataSetDescriptors = new ArrayList<DataSetDescriptor>();

      try
      {
         String[] values = (String[]) classLevelAnnotation.annotationType()
                                                          .getMethod("value")
                                                          .invoke(classLevelAnnotation);
         List<String> dataSetFileNames = Arrays.asList(values);
         if (dataSetFileNames.isEmpty() || dataSetFileNames.get(0).isEmpty())
         {
            Format format = configuration.getDefaultDataSetFormat();
            String defaultFileName = new DataSetFileNamingStrategy(format).createFileName(metadataExtractor.getJavaClass());
            dataSetFileNames.add(defaultFileName);
         }

         for (String dataFileName : dataSetFileNames)
         {
            dataSetDescriptors.add(createDataSetDescriptor(dataFileName));
         }

      }
      catch (Exception e)
      {
         throw new MetadataProcessingException("Unable to evaluate annotation value", e);
      }

      return dataSetDescriptors;
   }

   List<Format> getDataFormats(Method testMethod)
   {
      final List<Format> formats = new ArrayList<Format>();
      for (String dataFileName : getDataFileNames(testMethod))
      {
         formats.add(inferFormat(dataFileName));
      }
      return formats;
   }

   List<String> getDataFileNames(Method testMethod)
   {
      UsingDataSet dataAnnotation = getDataAnnotation(testMethod);
      String[] specifiedFileNames = dataAnnotation.value();
      if (specifiedFileNames.length == 0 || "".equals(specifiedFileNames[0].trim()))
      {
         return Arrays.asList(getDefaultNamingForDataSetFile(testMethod));
      }
      return Arrays.asList(specifiedFileNames);
   }

   List<Format> getExpectedDataFormats(Method testMethod)
   {
      final List<Format> formats = new ArrayList<Format>();
      for (String dataFileName : getExpectedDataFileNames(testMethod))
      {
         formats.add(inferFormat(dataFileName));
      }
      return formats;
   }

   List<String> getExpectedDataFileNames(Method testMethod)
   {
      ShouldMatchDataSet expectedAnnotation = getExpectedAnnotation(testMethod);
      String[] specifiedFileNames = expectedAnnotation.value();
      if (specifiedFileNames.length == 0 || "".equals(specifiedFileNames[0].trim()))
      {
         return Arrays.asList(getDefaultNamingForExpectedDataSetFile(testMethod));
      }
      return Arrays.asList(specifiedFileNames);
   }

   private String getDefaultNamingForDataSetFile(Method testMethod)
   {
      Format format = configuration.getDefaultDataSetFormat();

      if (metadataExtractor.usingDataSet().isDefinedOn(testMethod))
      {
         return new DataSetFileNamingStrategy(format).createFileName(metadataExtractor.getJavaClass(), testMethod);
      }

      return new DataSetFileNamingStrategy(format).createFileName(metadataExtractor.getJavaClass());
   }


   private String getDefaultNamingForExpectedDataSetFile(Method testMethod)
   {
      Format format = configuration.getDefaultDataSetFormat();

      if (metadataExtractor.shouldMatchDataSet().isDefinedOn(testMethod))
      {
         return new ExpectedDataSetFileNamingStrategy(format).createFileName(metadataExtractor.getJavaClass(), testMethod);
      }

      return new ExpectedDataSetFileNamingStrategy(format).createFileName(metadataExtractor.getJavaClass());
   }

   private UsingDataSet getDataAnnotation(Method testMethod)
   {
      return metadataExtractor.usingDataSet().getUsingPrecedence(testMethod);
   }

   private ShouldMatchDataSet getExpectedAnnotation(Method testMethod)
   {
      return metadataExtractor.shouldMatchDataSet().getUsingPrecedence(testMethod);
   }

}
