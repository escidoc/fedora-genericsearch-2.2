package dk.defxws.fedoragsearch.test;

import java.io.IOException;
import java.text.Collator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparatorSource;


public class ComparatorSourceTest1 extends FieldComparatorSource {

    @Override
    public FieldComparator newComparator(final String fieldname, final int numHits, final int sortPos,
            final boolean reversed) throws IOException {
        return new MultiFieldComparator(numHits, fieldname);
    }

    public class MultiFieldComparator extends FieldComparator {

        private final String[] fieldValues;
        private String[] currentReaderFieldValues;
        private int bottom;
        private final String fieldName;

        private Collator collator;
        private Pattern pattern = Pattern.compile(
                "([\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc])");
        private Matcher matcher1 = pattern.matcher("");
        private Matcher matcher2 = pattern.matcher("");

        MultiFieldComparator(final int numHits, final String fieldName) {
            fieldValues = new String[numHits];
            this.fieldName = fieldName;
            this.collator = Collator.getInstance();
            collator.setStrength(Collator.SECONDARY);

        }

        @Override
        public int compare(final int slot1, final int slot2) {
            String fieldValue1 = fieldValues[slot1];
            String fieldValue2 = fieldValues[slot2];
            return compare(fieldValue1, fieldValue2);
        }

        @Override
        public int compareBottom(final int doc) {
            final String fieldValue1 = fieldValues[bottom];
            final String fieldValue2 = currentReaderFieldValues[doc];
            return compare(fieldValue1, fieldValue2);
        }

        @Override
        public void copy(final int slot, final int doc) {
            fieldValues[slot] = currentReaderFieldValues[doc];
        }

        /**
         * {@inheritDoc}
         * <p>
         * 
         */
        @Override
        public void setNextReader(final IndexReader reader, final int docBase) throws IOException {
            currentReaderFieldValues = FieldCache.DEFAULT.getStrings(reader, fieldName);
        }

        @Override
        public void setBottom(final int bottom) {
            this.bottom = bottom;
        }

        @Override
        public Comparable value(final int slot) {
            return fieldValues[slot];
        }
        
        private int compare(String fieldValue1, String fieldValue2) {
            int result = 0;
            try {
                if (fieldValue1 == null && fieldValue2 == null) {
                    return 0;
                } else if (fieldValue1 == null && fieldValue2 != null) {
                    return -1;
                } else if (fieldValue1 != null && fieldValue2 == null) {
                    return 1;
                }
                matcher1.reset(fieldValue1);
                matcher2.reset(fieldValue2);
                fieldValue1 = matcher1.replaceAll("$1e");
                fieldValue2 = matcher2.replaceAll("$1e");
                result = collator.compare(fieldValue1, fieldValue2);
            } catch (Exception e) {
            }
            return result;
            
        }
    }

}
