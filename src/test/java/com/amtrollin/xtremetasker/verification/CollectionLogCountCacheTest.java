package com.amtrollin.xtremetasker.verification;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CollectionLogCountCacheTest
{
    @Test
    public void dropsInvalidateOnlyCountsThatDependOnTheItem()
    {
        CountingService service = new CountingService();
        int[] satchels = {10878, 10879};
        int[] unrelated = {100, 101};
        assertEquals(0, service.countObtained(satchels));
        assertEquals(0, service.countObtained(unrelated));
        int reads = service.reads;

        for (int i = 0; i < 1000; i++) assertEquals(0, service.countObtained(satchels));
        assertEquals(reads, service.reads);

        service.storeItem(10878);
        assertEquals(0, service.countObtained(unrelated));
        assertEquals(reads, service.reads);
        assertEquals(1, service.countObtained(satchels));
        assertEquals(reads + 2, service.reads);
    }

    @Test
    public void aliasesAreCountedOnceAndRestoreAndResetInvalidateCounts()
    {
        CollectionLogService service = new CollectionLogService();
        int[] aliases = {10878, 25619, 10878};
        assertEquals(0, service.countObtained(aliases));
        service.storeItem(25619);
        assertEquals(1, service.countObtained(aliases));
        service.resetCachedItemIds();
        assertEquals(0, service.countObtained(aliases));
        service.restoreCachedItemState(Set.of(10878), Map.of(10878, 1L));
        assertEquals(1, service.countObtained(aliases));
    }

    @Test
    public void boundedCacheEvictionRetainsCorrectCounts()
    {
        CollectionLogService service = new CollectionLogService();
        int[] first = {10878};
        assertEquals(0, service.countObtained(first));
        for (int i = 0; i < 2200; i++) service.countObtained(new int[]{10000 + i});
        service.storeItem(10878);
        assertEquals(1, service.countObtained(first));
    }

    @Test
    public void callerCanReuseAndModifyAnItemArray()
    {
        CollectionLogService service = new CollectionLogService();
        int[] items = {10878};
        service.storeItem(10878);
        assertEquals(1, service.countObtained(items));
        items[0] = 100;
        assertEquals(0, service.countObtained(items));
        service.storeItem(100);
        assertEquals(1, service.countObtained(items));
    }

    private static final class CountingService extends CollectionLogService
    {
        private int reads;

        @Override
        public boolean isItemObtained(int itemId)
        {
            reads++;
            return super.isItemObtained(itemId);
        }
    }
}
