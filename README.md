FineEkstazi
=======

FineEkstazi is a tool for fine-grained regression test selection (RTS)
for Java programs. It is based on Ekstazi, and leverages the semantics
of Java to provide a more precise RTS.

# Installation
To install FineEkstazi, clone the repository and run the following command:
```
mvn clean install
```

# Usage
Set the following configuration in `$HOME/.ekstazirc`:
```
finerts=true
mrts=true
```
Optionally, add the following configuration to specify the format of the dependencies file:
```
dependencies.format=txt
```

Then, add the following dependency in `pom.xml`:
```
<plugin>
    <groupId>org.ekstazi</groupId>
    <artifactId>ekstazi-maven-plugin</artifactId>
    <version>5.3.1</version> 
    <executions>
        <execution>
        <id>ekstazi</id>
        <goals>
            <goal>select</goal>
        </goals>
        </execution>
    </executions>
</plugin>
```

Finally, run your tests with the following command:
```
mvn test
```