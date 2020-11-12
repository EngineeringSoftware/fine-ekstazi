/*
 * Copyright 2014-present Milos Gligoric
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ekstazi.scalatest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.ekstazi.Ekstazi;

public class ScalaTestMonitor {

    public static Object newInstance(Class<?> clz) throws InstantiationException, IllegalAccessException,
            ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException,
            InvocationTargetException {
        Class<?> ekstaziSuiteClass = clz.getClassLoader().loadClass(ScalaTestNames.EKSTAZI_SUITE_BIN);
        Constructor<?> ekstaziSuiteConstructor = ekstaziSuiteClass.getConstructor(String.class, Object.class);
        String name = clz.getCanonicalName();
        if (!Ekstazi.inst().isClassAffected(name)) {
            return ekstaziSuiteConstructor.newInstance(name, (Object) null);
        } else {
            Ekstazi.inst().beginClassCoverage(name);
            try {
                Object suite = clz.newInstance();
                return ekstaziSuiteConstructor.newInstance(name, suite);
            } finally {
                Ekstazi.inst().endClassCoverage(name, true);
            }
        }
    }
}
