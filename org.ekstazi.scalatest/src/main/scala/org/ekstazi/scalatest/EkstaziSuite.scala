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

import org.scalatest.Suite;
import org.scalatest.Args;
import org.scalatest.Status;
import org.scalatest.SucceededStatus;

import org.ekstazi.Ekstazi;

class EkstaziSuite(className: String, wrapped: Object) extends Suite {

    override def run(testName: Option[String], args: Args): Status = {
        if (wrapped == null) {
            return SucceededStatus;
        } else {
            val name = wrapped.getClass().getCanonicalName();
            Ekstazi.inst().beginClassCoverage(name);
            val suite = wrapped.asInstanceOf[Suite];
            val status = suite.run(testName, args);
            Ekstazi.inst().endClassCoverage(name, !status.succeeds());
            return status;
        }
    }

    override def suiteName(): String = {
        if (wrapped == null) {
            return ("EkstaziNonSelected: " + className);
        } else {
            return wrapped.asInstanceOf[Suite].suiteName;
        }
    }
}
