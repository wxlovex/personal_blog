<script setup>
defineProps({ name: { type: String, required: true } })
const paths = {
  'lock': ['M7 10V7a5 5 0 0 1 10 0v3', 'M5 10h14v11H5z', 'M12 14v3'],
  'arrow-right': ['M4 12h16', 'm14 6 6 6-6 6'],
  'arrow-left': ['M20 12H4', 'm10 6-6 6 6 6'],
  'arrow-up-right': ['M6 18 18 6', 'M6 6h12v12'],
  'chevron-right': ['m9 5 7 7-7 7'],
  'chevron-left': ['m15 5-7 7 7 7'],
  'pen': ['m15 4 5 5', 'm4 20 4-1 12-12a2.8 2.8 0 0 0-4-4L4 15l-1 6 5-2'],
  'eye': ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0'],
  'heart': ['M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z'],
  'book': ['M12 6c-3-2-6-2-10-1v15c4-1 7-1 10 1 3-2 6-2 10-1V5c-4-1-7-1-10 1Zm0 0v15'],
  'folder': ['M3 7V5h6l2 2h10v13H3V7Z'],
  'globe': ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', 'M3 12h18', 'M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z'],
  'trash': ['M3 6h18', 'M9 6V3h6v3', 'm5 6 1 15h12l1-15', 'M10 10v7', 'M14 10v7'],
  'logout': ['M9 4H4v16h5', 'M9 12h12', 'm16 7 5 5-5 5'],
  'message': ['M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5a9.5 9.5 0 1 1 19 0Z', 'M7 10h10', 'M7 14h6'],
  'alert': ['m12 3 10 18H2L12 3Z', 'M12 9v5', 'M12 17h.01'],
  'clock': ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', 'M12 7v5l3 2'],
}
</script>
<template>
  <svg class="icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <path v-for="(d, index) in paths[name] || []" :key="index" :d="d" />
  </svg>
</template>
