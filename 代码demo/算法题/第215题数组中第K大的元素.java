package 算法题;

public class 第215题数组中第K大的元素 {
    //[2,3,5,4,9,8,3,3,9,8,2,3,4]
    public int findKthLargest(int[] nums, int k) {
        if (nums.length == 0 || k > nums.length) {
            return -1;
        }
        int[] heap = new int[k];
        for (int i = 0; i < k; i++) {
            heap[i] = nums[i];
        }
        buildHeap(heap);
        for (int i = k; i < nums.length; i++) {
            if (nums[i] > heap[0]) {
                heap[0] = nums[i];
                heap(heap, 0);
            }
        }
        return heap[0];
    }

    private void buildHeap(int[] heap) {
        for (int i = heap.length / 2 - 1; i >= 0; i--) {
            heap(heap, i);
        }
    }

    private void heap(int[] heap, int cur) {
        int left = 2 * cur + 1;
        int right = 2 * cur + 2;
        int min = cur;
        if (left < heap.length && heap[left] < heap[min]) {
            min = left;
        }
        if (right < heap.length && heap[right] < heap[min]) {
            min = right;
        }
        if (min != cur) {
            swap(heap, min, cur);
            heap(heap, min);
        }
    }

    private void swap(int[] heap, int a, int b) {
        int temp = heap[a];
        heap[a] = heap[b];
        heap[b] = temp;
    }
}
