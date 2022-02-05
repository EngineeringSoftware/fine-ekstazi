package org.ekstazi.smethods;
import java.util.*;
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
