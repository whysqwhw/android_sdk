/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.util.Pair;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a class and its methods/fields.
 *
 * {@link #getSince()} gives the API level it was introduced.
 *
 * {@link #getMethod} returns when the method was introduced.
 * {@link #getField} returns when the field was introduced.
 */
public class ApiClass {

    private final String mName;
    private final int mSince;

    private final List<Pair<String, Integer>> mSuperClasses = Lists.newArrayList();
    private final List<Pair<String, Integer>> mInterfaces = Lists.newArrayList();

    private final Map<String, Integer> mFields = new HashMap<String, Integer>();
    private final Map<String, Integer> mMethods = new HashMap<String, Integer>();

    ApiClass(String name, int since) {
        mName = name;
        mSince = since;
    }

    /**
     * Returns the name of the class.
     * @return the name of the class
     */
    String getName() {
        return mName;
    }

    /**
     * Returns when the class was introduced.
     * @return the api level the class was introduced.
     */
    int getSince() {
        return mSince;
    }

    /**
     * Returns when a field was added, or null if it doesn't exist.
     * @param name the name of the field.
     * @param info the corresponding info
     */
    Integer getField(String name, Api info) {
        // The field can come from this class or from a super class or an interface
        // The value can never be lower than this introduction of this class.
        // When looking at super classes and interfaces, it can never be lower than when the
        // super class or interface was added as a super class or interface to this clas.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the interface C was added.
        // Looking for SOME_FIELD we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became an interface
        int min = Integer.MAX_VALUE;
        Integer i = mFields.get(name);
        if (i != null) {
            min = i;
        }

        // now look at the super classes
        for (Pair<String, Integer> superClassPair : mSuperClasses) {
            ApiClass superClass = info.getClass(superClassPair.getFirst());
            if (superClass != null) {
                i = superClass.getField(name, info);
                if (i != null) {
                    int tmp = superClassPair.getSecond() > i ? superClassPair.getSecond() : i;
                    if (tmp < min) {
                        min = tmp;
                    }
                }
            }
        }

        // now look at the interfaces
        for (Pair<String, Integer> superClassPair : mInterfaces) {
            ApiClass superClass = info.getClass(superClassPair.getFirst());
            if (superClass != null) {
                i = superClass.getField(name, info);
                if (i != null) {
                    int tmp = superClassPair.getSecond() > i ? superClassPair.getSecond() : i;
                    if (tmp < min) {
                        min = tmp;
                    }
                }
            }
        }

        return min;
    }

    /**
     * Returns when a method was added, or null if it doesn't exist. This goes through the super
     * class to find method only present there.
     * @param methodSignature the method signature
     */
    int getMethod(String methodSignature, Api info) {
        // The method can come from this class or from a super class.
        // The value can never be lower than this introduction of this class.
        // When looking at super classes, it can never be lower than when the super class became
        // a super class of this class.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the super class changes to C.
        // Looking for foo() we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became the super class.
        int min = Integer.MAX_VALUE;
        Integer i = mMethods.get(methodSignature);
        if (i != null) {
            min = i;
        }

        // now look at the super classes
        for (Pair<String, Integer> superClassPair : mSuperClasses) {
            ApiClass superClass = info.getClass(superClassPair.getFirst());
            if (superClass != null) {
                i = superClass.getMethod(methodSignature, info);
                if (i != null) {
                    int tmp = superClassPair.getSecond() > i ? superClassPair.getSecond() : i;
                    if (tmp < min) {
                        min = tmp;
                    }
                }
            }
        }

        // now look at the interfaces classes
        for (Pair<String, Integer> interfacePair : mInterfaces) {
            ApiClass superClass = info.getClass(interfacePair.getFirst());
            if (superClass != null) {
                i = superClass.getMethod(methodSignature, info);
                if (i != null) {
                    int tmp = interfacePair.getSecond() > i ? interfacePair.getSecond() : i;
                    if (tmp < min) {
                        min = tmp;
                    }
                }
            }
        }

        return min;
    }

    void addField(String name, int since) {
        Integer i = mFields.get(name);
        if (i == null || i.intValue() > since) {
            mFields.put(name, Integer.valueOf(since));
        }
    }

    void addMethod(String name, int since) {
        // Strip off the method type at the end to ensure that the code which
        // produces inherited methods doesn't get confused and end up multiple entries.
        // For example, java/nio/Buffer has the method "array()Ljava/lang/Object;",
        // and the subclass java/nio/ByteBuffer has the method "array()[B". We want
        // the lookup on mMethods to associate the ByteBuffer array method to be
        // considered overriding the Buffer method.
        int index = name.indexOf(')');
        if (index != -1) {
            name = name.substring(0, index + 1);
        }

        Integer i = mMethods.get(name);
        if (i == null || i.intValue() > since) {
            mMethods.put(name, Integer.valueOf(since));
        }
    }

    void addSuperClass(String superClass, int since) {
        addToArray(mSuperClasses, superClass, since);
    }

    void addInterface(String interfaceClass, int since) {
        addToArray(mInterfaces, interfaceClass, since);
    }

    void addToArray(List<Pair<String, Integer>> list, String name, int value) {
        // check if we already have that name (at a lower level)
        for (Pair<String, Integer> pair : list) {
            if (name.equals(pair.getFirst())) {
                return;
            }
        }

        list.add(Pair.of(name, Integer.valueOf(value)));

    }

    @Override
    public String toString() {
        return mName;
    }

    /**
     * Returns the set of all methods, including inherited
     * ones.
     *
     * @param info the api to look up super classes from
     * @return a set containing all the members fields
     */
    Set<String> getAllMethods(Api info) {
        Set<String> members = new HashSet<String>(100);
        addAllMethods(info, members);

        return members;
    }

    private void addAllMethods(Api info, Set<String> set) {
        for (String method : mMethods.keySet()) {
            set.add(method);
        }

        for (Pair<String, Integer> superClass : mSuperClasses) {
            ApiClass clz = info.getClass(superClass.getFirst());
            assert clz != null : superClass.getSecond();
            if (clz != null) {
                clz.addAllMethods(info, set);
            }
        }

        // Get methods from implemented interfaces as well;
        for (Pair<String, Integer> superClass : mInterfaces) {
            ApiClass clz = info.getClass(superClass.getFirst());
            assert clz != null : superClass.getSecond();
            if (clz != null) {
                clz.addAllMethods(info, set);
            }
        }
    }

    /**
     * Returns the set of all fields, including inherited
     * ones.
     *
     * @param info the api to look up super classes from
     * @return a set containing all the fields
     */
    Set<String> getAllFields(Api info) {
        Set<String> members = new HashSet<String>(100);
        addAllFields(info, members);

        return members;
    }

    private void addAllFields(Api info, Set<String> set) {
        for (String field : mFields.keySet()) {
            set.add(field);
        }

        for (Pair<String, Integer> superClass : mSuperClasses) {
            ApiClass clz = info.getClass(superClass.getFirst());
            assert clz != null : superClass.getSecond();
            if (clz != null) {
                clz.addAllFields(info, set);
            }
        }

        // Get methods from implemented interfaces as well;
        for (Pair<String, Integer> superClass : mInterfaces) {
            ApiClass clz = info.getClass(superClass.getFirst());
            assert clz != null : superClass.getSecond();
            if (clz != null) {
                clz.addAllFields(info, set);
            }
        }
    }
}
