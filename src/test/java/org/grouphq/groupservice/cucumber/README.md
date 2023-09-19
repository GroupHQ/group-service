This folder contains the files necessary to configure cucumber to work with 
Spring and JUnit 5.

## RunCucumberTest
Sets up the JUnit Test Suite using the Cucumber engine, each are separate 
dependencies in the project's build.gradle file.

The annotations below are provided by the JUnit Platform Suite API dependency.
A brief description of each is follows.

### @Suite
Marks this class as a JUnit Test Suite. Think of it as a container to run tests that
are managed by a JUnit engine you specify.

### @IncludeEngines("cucumber")
Specifies the engines to include in the test suite. Think of it as the suite controller. 
The Cucumber engine here controls what tests are run, and based on some more info that we 
give it, it can run our Cucumber tests for us.

### @SelectClasspathResource("features")
By default, when you build your project using Gradle, it compiles your `main` and `test` folders 
and places them under the `build` folder. All your files in these folders are placed under
the `build/classes` folder, _EXCEPT_ your `resources` folder. Gradle places these folders
under the `build/resources/main` or `build/resources/test` depending on if they were under
the `main` or `test` folder, respectively.

With that out of the way, we can explain the **@SelectClasspathResource** means. Essentially,
it's the folder that contains the resources for our test suite engine. Cucumber expects a resource 
folder with files it recognizes (likes .feature files). Since we store our .feature files under the
`test/resources/features` folder, we want the engine to look under that folder. By default,
Gradle compiles the `test/resources` folder to the `build/resources/test` folder. This folder
contains all the files under `test/resources`. The JUnit Test Suite already expects any path 
we give it to be under that folder, so we need to give it the path assuming we are in the 
`test/resources` directory. That's simply `features`.

### @ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
This is used by the Cucumber engine to enable the "pretty" plugin for beautifying 
Cucumber test output results.

### @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "org.grouphq.groupservice.cucumber")
This property is required. It informs the Cucumber engine where to start looking for your "glue" files.
These are any files that Cucumber needs to run the feature tests, including the step definitions, and 
any additional configuration needed (such as configuring Cucumber with Spring using).
definitions. The path must be relative to the `test/java` folder (these files are compiled to 
the `build/java/test` folder where Cucumber looks. The folder structure in this directory is identical
to the `test/java` folder, unlike the `build/resources` folder mentioned above).

In our case, we store the "glue" files starting in the `test/java/com/grouphq/groupservice/cucumber/`. This
folder contains the `feature_steps` folder for our step definitions and the `CucumberSpringConfiguration` class
needed to link Cucumber with the Spring Context. Since all files in `test/java` are compiled to the `build/test/java`,
folder, the value relative to `test/java` following package notation is `org.grouphq.groupservice.cucumber`.

## CucumberSpringConfiguration
This class is used to link Cucumber with the Spring Context so that it can use the features and beans offered
by the Spring framework.

### @CucumberContextConfiguration
Informs Cucumber to check the current class for the context configuration.

### @SpringBootTest
Loads the complete application context for the Spring application. Allows you to use these beans in your tests.

### @AutoConfigureWebTestClient
By default, the @SpringBootTest annotation does not provide a WebTestClient bean. To get this bean, you need to include
the @AutoConfigureWebTestClient. Note that while you can technically use @WebFluxTest, this is meant to give you a slice
of the application context related to Spring WebFlux, and this includes the WebTestClient. If you're already loading the 
application context, and you need the WebTestClient bean, then @AutoConfigureWebTestClient is a more specific annotation 
to convey your intentions.