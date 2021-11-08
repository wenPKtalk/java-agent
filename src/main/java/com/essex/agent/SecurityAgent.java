package com.essex.agent;


import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.stream.Stream;


public class SecurityAgent {
    /**
     * 如果Agent是通过JVM选项的方式捆绑到程序中，则在JVM初化完毕后，会执行premain方法，premain执行之后才是程序的main方法。
     * 清单文件中需要指定Premain-Class
     * <p>
     * premain有两种形式，默认会执行1), 如果没有1)则会执行2), 1)和2)只会执行一个<br>
     * <code>
     * 1) public static void premain(String agentArgs, Instrumentation instrumentation)<br/>
     * 2) public static void premain(String agentArgs)
     * </code></p>
     *
     * @param agentArgs
     * @param instrumentation
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws InvocationTargetException {
        System.out.println("CustomAgent#premain(String agentArgs, Instrumentation instrumentation)");
        //set property
        System.setProperty("MY_PROPERTY", "this is property");
        //set system environment by hack way
        setenv("MY_ENV", "this is env");
        System.out.println("After get property=" + System.getProperty("MY_PROPERTY"));
        System.out.println("After get env=" + System.getProperty("MY_ENV"));
    }

    public static void premain(String agentArgs) throws InvocationTargetException {
        System.out.println("CustomAgent#premain(String agentArgs)");
        parseAgentArgs(agentArgs);
    }

    /**
     * 如果Agent是在程序运行过程中，动态的捆绑到程序中，则是执行agentmain方法。
     * 清单文件中要指定 Agent-Class
     * <p>
     * agentmain有两种形式，默认会执行1), 如果没有1)则会执行2), 1)和2)只会执行一个<br>
     * <code>
     * 1) public static void agentmain(String agentArgs, Instrumentation instrumentation)<br/>
     * 2) public static void agentmain(String agentArgs)
     * </code></p>
     * <p>
     * 通过程序捆绑的代码：<br/>
     * <code>
     * VirtualMachine vm=VirtualMachine.attach("PID"); //给指定的进程捆绑agent<br/>
     * 在得到目标进程的vm后，就可以通过
     * vm.loadAgent("agentjar"),vm.loadAgentLibrary(dll), and loadAgentPath(dllPath) 进行捆绑操作了 <br/>
     * 其中:<br>
     * loadAgent是捆绑一个jar文件，
     * loadAgentLibrary,loadAgentPath则是捆绑本地方法库（动态连接库）
     * </code>
     *
     * @param agentArgs
     * @param inst
     */
    public static void agentmain(String agentArgs, Instrumentation inst) throws InvocationTargetException {
        System.out.println("CustomAgent#agentmain(String agentArgs, Instrumentation instrumentation)");
        parseAgentArgs(agentArgs);
    }

    public static void agentmain(String agentArgs) throws InvocationTargetException {
        System.out.println("CustomAgent#agentmain(String agentArgs)");
        parseAgentArgs(agentArgs);
    }

    /*** 不论是premain,还在agentmain,都可以指定参数，参数是一个字符串，具体怎么解析，是程序自己的事* @param agentArgs* @return* @throws IOException* @throws AttachNotSupportedException*/
    private static boolean parseAgentArgs(String agentArgs) throws InvocationTargetException {
        boolean hasArgs = false;
        if (agentArgs != null && !agentArgs.isEmpty()) {
            System.out.println("agentArgs is : " + agentArgs);
            hasArgs = true;
        } else {
            System.out.println("has no agentArgs .");
        }
        return hasArgs;
    }

    /**
     *
     * @see <a href="https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java">stackoverflow</a>
     * Sets an environment variable FOR THE CURRENT RUN OF THE JVM
     * Does not actually modify the system's environment variables,
     * but rather only the copy of the variables that java has taken,
     * and hence should only be used for testing purposes!
     *
     * @param key   The Name of the variable to set
     * @param value The value of the variable to set
     */
    @SuppressWarnings("unchecked")
    public static <K, V> void setenv(final String key, final String value) throws InvocationTargetException {
        try {
            /// we obtain the actual environment
            final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            final Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            final boolean environmentAccessibility = theEnvironmentField.isAccessible();
            theEnvironmentField.setAccessible(true);

            final Map<K, V> env = (Map<K, V>) theEnvironmentField.get(null);


            // This is triggered to work on openjdk 1.8.0_91
            // The ProcessEnvironment$Variable is the key of the map
            final Class<K> variableClass = (Class<K>) Class.forName("java.lang.ProcessEnvironment$Variable");
            final Method convertToVariable = variableClass.getMethod("valueOf", String.class);
            final boolean conversionVariableAccessibility = convertToVariable.isAccessible();
            convertToVariable.setAccessible(true);

            // The ProcessEnvironment$Value is the value fo the map
            final Class<V> valueClass = (Class<V>) Class.forName("java.lang.ProcessEnvironment$Value");
            final Method convertToValue = valueClass.getMethod("valueOf", String.class);
            final boolean conversionValueAccessibility = convertToValue.isAccessible();
            convertToValue.setAccessible(true);

            if (value == null) {
                env.remove(convertToVariable.invoke(null, key));
            } else {
                // we place the new value inside the map after conversion so as to
                // avoid class cast exceptions when rerunning this code
                env.put((K) convertToVariable.invoke(null, key), (V) convertToValue.invoke(null, value));

                // reset accessibility to what they were
                convertToValue.setAccessible(conversionValueAccessibility);
                convertToVariable.setAccessible(conversionVariableAccessibility);
            }
            // reset environment accessibility
            theEnvironmentField.setAccessible(environmentAccessibility);

            // we apply the same to the case insensitive environment
            final Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            final boolean insensitiveAccessibility = theCaseInsensitiveEnvironmentField.isAccessible();
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            // Not entirely sure if this needs to be casted to ProcessEnvironment$Variable and $Value as well
            final Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            if (value == null) {
                // remove if null
                cienv.remove(key);
            } else {
                cienv.put(key, value);
            }
            theCaseInsensitiveEnvironmentField.setAccessible(insensitiveAccessibility);
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">", e);
        } catch (final NoSuchFieldException e) {
            // we could not find theEnvironment
            final Map<String, String> env = System.getenv();
            Stream.of(Collections.class.getDeclaredClasses())
                    // obtain the declared classes of type $UnmodifiableMap
                    .filter(c1 -> "java.util.Collections$UnmodifiableMap".equals(c1.getName()))
                    .map(c1 -> {
                        try {
                            return c1.getDeclaredField("m");
                        } catch (final NoSuchFieldException e1) {
                            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + "> when locating in-class memory map of environment", e1);
                        }
                    })
                    .forEach(field -> {
                        try {
                            final boolean fieldAccessibility = field.isAccessible();
                            field.setAccessible(true);
                            // we obtain the environment
                            final Map<String, String> map = (Map<String, String>) field.get(env);
                            if (value == null) {
                                // remove if null
                                map.remove(key);
                            } else {
                                map.put(key, value);
                            }
                            // reset accessibility
                            field.setAccessible(fieldAccessibility);
                        } catch (final ConcurrentModificationException e1) {
                            // This may happen if we keep backups of the environment before calling this method
                            // as the map that we kept as a backup may be picked up inside this block.
                            // So we simply skip this attempt and continue adjusting the other maps
                            // To avoid this one should always keep individual keys/value backups not the entire map
                            System.out.println("Attempted to modify source map: " + field.getDeclaringClass() + "#" + field.getName());
                        } catch (final IllegalAccessException e1) {
                            throw new IllegalStateException("Failed setting environment variable <" + key + "> to <" + value + ">. Unable to access field!", e1);
                        }
                    });
        }
        System.out.println("Set environment variable <" + key + "> to <" + value + ">. Sanity Check: " + System.getenv(key));
    }
}
