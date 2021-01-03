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

package org.ekstazi.check;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.ekstazi.Config;
import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.changelevel.FineTunedBytecodeCleaner;
import org.ekstazi.data.RegData;
import org.ekstazi.data.Storer;
import org.ekstazi.hash.Hasher;
import org.ekstazi.util.FileUtil;

abstract class AbstractCheck {

    /** Storer */
    protected final Storer mStorer;

    /** Hasher */
    protected final Hasher mHasher;

    protected static HashMap<String, Boolean> fileChangedCache = new HashMap<>();
    /**
     * Constructor.
     */
    public AbstractCheck(Storer storer, Hasher hasher) {
        this.mStorer = storer;
        this.mHasher = hasher;
    }

    public abstract String includeAll(String fileName, String fileDir);

    public abstract void includeAffected(Set<String> affectedClasses);

    protected boolean isAffected(String dirName, String className, String methodName) {
        return isAffected(mStorer.load(dirName, className, methodName));
    }

    protected boolean isAffected(Set<RegData> regData) {
        return regData == null || regData.size() == 0 || hasHashChanged(regData);
    }

    /**
     * Check if any element of the given set has changed.
     */
    private boolean hasHashChanged(Set<RegData> regData) {
        for (RegData el : regData) {
            if (hasHashChanged(mHasher, el)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given datum has changed using the given hasher.
     */
    protected final boolean hasHashChanged(Hasher hasher, RegData regDatum) {
        String urlExternalForm = regDatum.getURLExternalForm();
        // Check hash.
        String newHash = hasher.hashURL(urlExternalForm);
        boolean anyDiff = !newHash.equals(regDatum.getHash());
        // TODO: if checksum of ekstazi differs, compare ChangeTypes
        if (Config.FINERTS_ON_V && anyDiff && urlExternalForm.contains("target")) {
            String fileName = FileUtil.urlToObjFilePath(urlExternalForm);
            Boolean changed = fileChangedCache.get(fileName);
//            System.out.println("AbstractCheck ChangeTypes.fileChanged: " + fileChanged);
            if (changed != null){
//                System.out.println("AbstractCheck: " + changed + " " + fileName);
                return changed;
            }
            ChangeTypes curChangeTypes;
            try {
                ChangeTypes preChangeTypes = ChangeTypes.fromFile(fileName);
                File curClassFile = new File(urlExternalForm.substring(urlExternalForm.indexOf("/")));
                if (!curClassFile.exists()) {
                    changed = true;
                } else {
                    curChangeTypes = FineTunedBytecodeCleaner.removeDebugInfo(FileUtil.readFile(
                            new File(urlExternalForm.substring(urlExternalForm.indexOf("/")))));
                    changed = preChangeTypes == null || !preChangeTypes.equals(curChangeTypes);
                }
                fileChangedCache.put(fileName, changed);
                return changed;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return anyDiff;
    }

}
