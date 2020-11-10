package org.ekstazi.changelevel;

import org.ekstazi.asm.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.ekstazi.util.FileUtil;

public class FineTunedBytecodeCleaner extends ClassVisitor {

    private TreeMap<String, String> constructorsMap = new TreeMap<>();
    private TreeMap<String, String> instanceMethodMap = new TreeMap<>();
    private TreeMap<String, String> staticMethodMap = new TreeMap<>();
    private TreeMap<String, String> methodMap = new TreeMap<>();
    private TreeMap<String, String> instanceFieldMap = new TreeMap<>();
    private TreeMap<String, String> staticFieldMap = new TreeMap<>();
    private HashMap<String, String> exceptionMap = new HashMap<>();
    private HashMap<String, String> annotations = new HashMap<>();
    private int classModifier;
    private String[] classInterfaces;
    private String superClass = "";
    private String curClass = "";

    public FineTunedBytecodeCleaner(int api, ClassVisitor cv) {
        super(api, cv);
    }

    // class name
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classModifier = access;

        classInterfaces = interfaces;
        Arrays.sort(classInterfaces);

        curClass = name;
        superClass = superName;

        super.visit(version, access, name, signature, superName, interfaces);
    }


    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldVisitor fv = cv.visitField(access, name, desc, signature, value);

        boolean isStatic = (access > Opcodes.ACC_STATIC);
        if (isStatic){
            staticFieldMap.put(name + desc, access + " ");
        }else{
            instanceFieldMap.put(name + desc, access + " ");
        }
        return fv;

        //todo field annotation
//        Printer p = new CleanCodeUtil(Opcodes.ASM6) {
//            @Override
//            public void visitFieldEnd() {
//                StringWriter sw = new StringWriter();
//                print(new PrintWriter(sw));
//                getText();
//                String field = sw.toString();
//                annotations.add(field);
//                super.visitFieldEnd();
//            }
//        };
//        return new FineTunedFieldVisitor(fv, p);

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

        Printer p = new CleanCodeUtil(){
            @Override
            public void visitMethodEnd() {
                StringWriter sw = new StringWriter();
                print(new PrintWriter(sw));
                getText().clear();

                String methodBody = sw.toString();
                String methodSignature = name + desc;

                List<String> excep = new ArrayList<>();
                if (exceptions != null) {
                    for (String el : exceptions) {
                        excep.add(el);
                    }
                }
                Collections.sort(excep);
                exceptionMap.put(name+desc, excep.toString());

                if((name+desc).equals("<clinit>()V")) {
                    // initialize static field
                    constructorsMap.put(methodSignature, sortedString(methodBody));
                } else{
                    boolean isStatic = (access > Opcodes.ACC_STATIC);
                    if(isStatic){
                        staticMethodMap.put(methodSignature, methodBody);
                        methodMap.put(methodSignature, methodBody);
                    }else{
                        if (methodSignature.contains("<init>")) {
                            constructorsMap.put(methodSignature, sortedString(methodBody));
                        }else{
                            instanceMethodMap.put(methodSignature, methodBody);
                            methodMap.put(methodSignature, methodBody);
                        }
                    }
                }
            }
        };

        return new FineTunedMethodVisitor(mv, p);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = cv.visitAnnotation(desc, visible);
        Printer p = new CleanCodeUtil(){
            // class annotation
            @Override
            public void visitAnnotationEnd() {
                if (visible) {
                    // todo: runtime annotation of class
//                    StringWriter sw = new StringWriter();
//                    print(new PrintWriter(sw));
//                    getText().clear();
//                    String annotation = sw.toString();
//                    if (!annotation.equals(""))
//                        annotations.add(annotation);
                }
            }
        };
        return new TraceAnnotationVisitor(av, p);
    }

    class FineTunedFieldVisitor extends TraceFieldVisitor {
        public FineTunedFieldVisitor(FieldVisitor fv, Printer p) {
            super(fv, p);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String desc,
                                                 final boolean visible) {
            AnnotationVisitor av = fv == null ? null : fv.visitAnnotation(desc,
                    visible);
            if(visible) {
                return new FineTunedAnnotationVisitor(av, p);
            }
            return av;
        }
    }

    class FineTunedMethodVisitor extends TraceMethodVisitor {

        public FineTunedMethodVisitor(MethodVisitor mv, Printer p) {
            super(mv, p);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            if(visible) {
                return new FineTunedAnnotationVisitor(p);
            }
            return super.visitParameterAnnotation(parameter, desc, visible);
        }

    }

    static class FineTunedAnnotationVisitor extends TraceAnnotationVisitor{
        public Printer p;

        public FineTunedAnnotationVisitor(AnnotationVisitor av, Printer p) {
            super(av, p);
            this.p = p;
        }

        public FineTunedAnnotationVisitor(Printer p) {
            super(p);
            this.p = p;
        }

        @Override
        public void visit(String name, Object value) {
            p.buf.setLength(0);
            p.buf.append(name+value);
            p.text.add(p.buf.toString());
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    public ChangeTypes getChangeTypes(){
        ChangeTypes c = new ChangeTypes();
        c.annotations = this.annotations;
        c.classInterfaces = this.classInterfaces;
        c.classModifier = this.classModifier;
        c.constructorsMap = this.constructorsMap;
        c.instanceMethodMap = this.instanceMethodMap;
        c.staticMethodMap = this.staticMethodMap;
        c.instanceFieldMap = this.instanceFieldMap;
        c.staticFieldMap = this.staticFieldMap;
        c.curClass = this.curClass;
        c.superClass = this.superClass;
        c.methodMap = this.methodMap;
        return c;
    }

    public TreeMap getConstructorsMap(){
        return constructorsMap;
    }

    public TreeMap getInstanceMethodMap(){
        return instanceMethodMap;
    }

    public TreeMap getStaticMethodMap(){
        return staticMethodMap;
    }

    public String[] getClassInterfaces(){
        return classInterfaces;
    }

    public String getSuperClass(){
        return superClass;
    }

    public int getClassModifier(){
        return classModifier;
    }

    public TreeMap getInstanceFieldMap(){
        return instanceFieldMap;
    }

    public TreeMap getStaticFieldMap(){
        return staticFieldMap;
    }

    public HashMap<String, String> getAnnotations(){
        return annotations;
    }

    public HashMap getExceptionMap(){
        return exceptionMap;
    }


    private String sortedString(String str){
        return str.chars() // IntStream
                .sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public static ChangeTypes removeDebugInfo(byte[] bytes) {
        ChangeTypes c = new ChangeTypes();
        if (bytes.length >= 4) {
            // Check magic number.
            int magic = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                    | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
            if (magic != 0xCAFEBABE)
                return c;
        } else {
            return c;
        }

        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            FineTunedBytecodeCleaner visitor = new FineTunedBytecodeCleaner(Opcodes.ASM6, writer);
            reader.accept(visitor, ClassReader.SKIP_DEBUG);
            c = visitor.getChangeTypes();
            return c;
        } catch (Exception ex) {
            return c;
        }
    }

    static String INNER_ACCESS = "add_access"; // 1
    //todo
    static String ADD_BASE_CLASS = "add_base_class"; // 2
    static String ADD_CLASS = "add_class";  // 3
    static String ADD_CONSTRUCTOR = "add_constructor"; // 4
    static String ADD_FIELD = "add_field"; // 5
    static String ADD_INSTANCE_METHOD = "add_instance_method"; // 6
    //todo
    static String ADD_RUNTIME_ANNOTATION = "add_runtime_annotation"; // 7
    //todo
    static String ADD_STATIC_INITIALIZED_BLOCK = "add_static_initialized_block";
    static String ADD_STATIC_METHOD = "add_static_method"; // 8
    //todo
    static String CHANGE_MODIFIER_OF_FIELD = "change_modifier_of_field"; // 9
    static String CHANGE_BASE_CLASS = "change_base_class"; // 10
    static String CHANGE_CLASS_MODIFIER = "change_class_modifier"; // 11
    static String CHANGE_CONSTRUCTOR = "change_constructor"; // 12
    static String CHANGE_FIELD_DECLARATION = "change_field_declaration"; // 13
    static String CHANGE_FIELD_INITIALIZATION = "change_field_initialization"; // 14
    static String CHANGE_RUNTIME_ANNOTATION = "add_runtime_annotation"; // 15
    static String CHANGE_INTERFACE = "change_interface"; // 16
    static String CHANGE_METHOD_EXCEPTION = "change_method_exception"; // 17
    //todo
    static String PARAMETER_TYPE_SPECIALIZATION = "parameter_type_specialization"; // 18
    static String REMOVE_CLASS = "remove_class"; // 19
    static String REMOVE_STATIC_METHOD = "remove_static_method"; // 20
    static String REMOVE_FIELD = "remove_field"; // 21
    static String REMOVE_INSTANCE_METHOD = "remove_instance_method"; // 22
    static String REMOVE_CONSTRUCTOR = "remove_constructor";// 23
    static String USE_LAMBDA = "use_lambda"; // 24
    static String METHOD = "Method";
    static String MULMETHODS = "MulMethods";
    static String OTHER = "Other";

    // invoke bash in java
    public static String bashCommand(String path, String command) {
        String[] commands = {"bash", "-c", "cd " + path + ";" + command};
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(commands);
            p.waitFor();
            BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String str = error.lines().collect(Collectors.joining());
            if (str != null && !str.equals("")) {
                System.out.println("error info: " + str);
            }
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = "";
            while ((line = b.readLine()) != null) {
                sb.append(line + "\n");
            }
            b.close();
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Failed to execute bash with command: " + command);
            e.printStackTrace();
        }
        return "";
    }

    public static String getChangeLevel(String preClassPath, String curClassPath) {
        Set<String> res = new HashSet<>();
        File pref = new File(preClassPath);
        File curf = new File(curClassPath);
        if (!pref.exists()) { // class file from previous build does not exist
            return ADD_CLASS;
        } else if (!curf.exists()){ // class file from current build does not exist
            return REMOVE_CLASS;
        } else {
            try {
                byte[] preBytes = FileUtil.readFile(new File(preClassPath));
                ChangeTypes preCleanBytes = removeDebugInfo(preBytes);

                byte[] curBytes = FileUtil.readFile(new File(curClassPath));
                ChangeTypes curCleanBytes = removeDebugInfo(curBytes);

                System.out.println("diff: "+preCleanBytes.equals(curCleanBytes));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return OTHER;
    }

    // this method is used to classify change levels automatically
    public static void main(String[] args) {
        for (String project : Macros.projectList.keySet()) {
            String folderName = Macros.projectFolderPath;
            File folder = new File(folderName);
            if (!folder.isDirectory()) {
                bashCommand(".", "mkdir " + folderName);
            }
            String projectName = project.split("/")[1];
            String projectPath = folderName + "/" + projectName;
            File projectFolder = new File(projectPath);
            if (!projectFolder.isDirectory()) {
                bashCommand(folderName, "git clone https://github.com/" + project);
            }

            File resultFolder = new File(Macros.resultFolderPath);
            if (!resultFolder.exists() || !resultFolder.isDirectory()) {
                resultFolder.mkdirs();
            }
            //use json to store the result, here use the library Gson
            Map<String, Map<String, String>> resLinkedHashMap = new LinkedHashMap<>();
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            //get previous shas
            String sha = Macros.projectList.get(project);
            String[] shalist = bashCommand(projectPath, "git rev-list --first-parent -n " + (Macros.numSHA + 1) + " " + sha + " | tac").split("\n");
            String preSHA = shalist[0];

            for (int i = 1; i < shalist.length; i++) {
                Map<String, String> jsonObject = new LinkedHashMap<>();
                String curSHA = shalist[i];
                // compare the difference of previous class file and current class file
                bashCommand(projectPath, "git checkout " + preSHA);
                String preCompileResult = bashCommand(projectPath, "mvn test-compile -Drat.skip");
                bashCommand(projectPath, "cp -rf target/classes preclasses");
                bashCommand(projectPath, "cp -rf target/test-classes pretestclasses");
                bashCommand(projectPath, "git clean -f");
                bashCommand(projectPath, "rm -rf target");
                bashCommand(projectPath, "git checkout " + curSHA);
                String curCompileResult = bashCommand(projectPath, "mvn clean test-compile "+Macros.SKIPS);
                if (curCompileResult.contains("BUILD FAILURE")) {
                    bashCommand(projectPath, preSHA + " " + curSHA + "\n >> " + Macros.resultFolderPath + "/build_failure.log");
                    bashCommand(projectPath, "rm -rf preclasses");
                    bashCommand(projectPath, "rm -rf pretestclasses");
                    bashCommand(projectPath, "rm -rf target");
                    continue;
                }

                File curClassFolder = new File(projectPath + "/target/classes");
                File curTestFolder = new File(projectPath + "/target/test-classes");

                ChangeTypes.getHierarchyGraph(ChangeTypes.listFiles(projectPath + "/target/classes"));
                ChangeTypes.getHierarchyGraph(ChangeTypes.listFiles(projectPath + "/target/test-classes"));

                for (String curClassPath : ChangeTypes.listFiles(curClassFolder.getAbsolutePath())){
                    int index = curClassPath.lastIndexOf("target/classes");
                    String fileRelativePath = curClassPath.substring(index + "target/classes/".length());
                    String preClassPath = projectPath + "/preclasses/" + fileRelativePath;
                    String level = getChangeLevel(preClassPath, curClassPath);
//                    jsonObject.put(fileRelativePath, "class/method");
                }

                for (String curClassPath : ChangeTypes.listFiles(curTestFolder.getAbsolutePath())){
                    int index = curClassPath.lastIndexOf("target/test-classes");
                    String fileRelativePath = curClassPath.substring(index + "target/test-classes/".length());
                    String preClassPath = projectPath + "/pretestclasses/" + fileRelativePath;
                    String level = getChangeLevel(preClassPath, curClassPath);
//                    jsonObject.put(fileRelativePath, level);

                }

                resLinkedHashMap.put(curSHA, jsonObject);
                bashCommand(projectPath, "rm -rf preclasses");
                bashCommand(projectPath, "rm -rf pretestclasses");
                bashCommand(projectPath, "rm -rf target");
                preSHA = curSHA;
            }
            // generate json file
//            String res = gson.toJson(resLinkedHashMap, LinkedHashMap.class);
//            System.out.println(res);
//            try {
//                Files.write(Paths.get(Macros.resultFolderPath + "/" + projectName + ".json"), res.getBytes());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        }
    }

}