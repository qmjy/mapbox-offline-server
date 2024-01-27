/*
 * Copyright (c) 2024 QMJY.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package no.ecc.vectortile;

import java.util.Set;

/**
 * A filter which can be passed to a VectorTile decoder to optimize performance by only decoding layers of interest.
 */
public abstract class Filter {

    public abstract boolean include(String layerName);

    public static final Filter ALL = new Filter() {

        @Override
        public boolean include(String layerName) {
            return true;
        }

    };

    /**
     * A filter that only lets a single named layer be decoded.
     */
     public static final class Single extends Filter {

        private final String layerName;

        public Single(String layerName) {
            this.layerName = layerName;
        }

        @Override
        public boolean include(String layerName) {
            return this.layerName.equals(layerName);
        }

    }

    /**
     * A filter that only allows the named layers to be decoded.
     */
    public static final class Any extends Filter {

        private final Set<String> layerNames;

        public Any(Set<String> layerNames) {
            this.layerNames = layerNames;
        }

        @Override
        public boolean include(String layerName) {
            return this.layerNames.contains(layerName);
        }

    }

}