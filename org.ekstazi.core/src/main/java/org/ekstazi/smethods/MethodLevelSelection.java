package org.ekstazi.smethods;

import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.ekstazi.changelevel.FineTunedBytecodeCleaner.removeDebugInfo;
import static org.ekstazi.smethods.Macros.*;

public class MethodLevelSelection {

    public static Map<String, Set<String>> getInvokedConstructorsMap(Map<String, Set<String>> methodName2MethodNames){
        Map<String, Set<String>> method2invokedConstructors = new HashMap<>();
        for (String method : methodName2MethodNames.keySet()){
            if (!method.contains("(")){
                continue;
            }
            Set<String> invokedConstructors = new HashSet<>();
            for (String m : methodName2MethodNames.get(method)){
                if (m.contains("<init>")){
                    invokedConstructors.add(m);
                }
            }
            method2invokedConstructors.put(method, invokedConstructors);
        }
        return method2invokedConstructors;
    }

    public static Set<String> getChangedMethods(Set<String> allTests){
        Set<String> res = new HashSet<>();

        try {
            List<Path> classPaths = Files.walk(Paths.get(TEST_PROJECT_PATH))
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".class"))
                    .collect(Collectors.toList());

            Set<String> changeTypePaths = new HashSet<>();
            String serPath = TEST_PROJECT_PATH + "/" + EKSTAZI_ROOT_DIR_NAME + "/" + CHANGE_TYPES_DIR_NAME;
            if (new File(serPath).exists()) {
                changeTypePaths = Files.walk(Paths.get(serPath))
                        .filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .map(Path::toString)
                        .collect(Collectors.toSet());
            }

            for (Path classPath : classPaths){
                byte[] array = Files.readAllBytes(classPath);
                ChangeTypes curChangeTypes = removeDebugInfo(array);

                String changeTypePath = FileUtil.urlToObjFilePath(classPath.toUri().toURL().toExternalForm());
                File preChangeTypeFile = new File(changeTypePath);

                if (!preChangeTypeFile.exists()){
                    // does not exist before
                    Set<String> methods = new HashSet<>();
                    curChangeTypes.methodMap.keySet().forEach(m -> methods.add(curChangeTypes.curClass + "#" +
                            m.substring(0, m.indexOf(")")+1)));
                    curChangeTypes.constructorsMap.keySet().forEach(m -> methods.add(curChangeTypes.curClass + "#" +
                            m.substring(0, m.indexOf(")")+1)));
                    res.addAll(methods);
                }else {
                    changeTypePaths.remove(changeTypePath);
                    ChangeTypes preChangeTypes = ChangeTypes.fromFile(changeTypePath);

                    if (!preChangeTypes.equals(curChangeTypes)) {
                        res.addAll(getChangedMethodsPerChangeType(preChangeTypes.methodMap,
                                curChangeTypes.methodMap, curChangeTypes.curClass, allTests));
                        res.addAll(getChangedMethodsPerChangeType(preChangeTypes.constructorsMap,
                                curChangeTypes.constructorsMap, curChangeTypes.curClass, allTests));
                    }
                }
            }

            for(String preChangeTypePath : changeTypePaths){
                new File(preChangeTypePath).delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res;
    }


    static Set<String> getChangedMethodsPerChangeType(TreeMap<String, String> oldMethods, TreeMap<String, String> newMethods,
                                                      String className, Set<String> allTests){
        Set<String> res = new HashSet<>();
        //TODO: consider adding test class
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig){
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)){
                if (oldMethods.get(sig).equals(newMethods.get(sig))) {
                    oldMethods.remove(sig);
                    newMethods.remove(sig);
                }else{
                    res.add(className + "#" + sig.substring(0, sig.indexOf(")")+1));
                }
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))){
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))){
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
            }
        }
        // className is Test
        if (allTests.contains(className)){
            for (String sig : newMethods.keySet()){
                res.add(className + "#" + sig.substring(0, sig.indexOf(")")+1));
            }
        }
        return res;
    }
}
