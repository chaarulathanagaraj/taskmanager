import { useState, useMemo } from 'react';
import type { ProcessInfo } from '../types';

interface FilterOptions {
  searchTerm?: string;
  minCpu?: number;
  minMemory?: number;
  sortBy?: 'cpu' | 'memory' | 'threads' | 'handles' | 'name' | 'pid';
  sortOrder?: 'asc' | 'desc';
}

/**
 * Hook to filter and sort processes
 */
export const useProcessFilter = (processes: ProcessInfo[], options: FilterOptions = {}) => {
  const {
    searchTerm = '',
    minCpu = 0,
    minMemory = 0,
    sortBy = 'cpu',
    sortOrder = 'desc',
  } = options;

  const filteredProcesses = useMemo(() => {
    let filtered = [...processes];

    // Search filter
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (p) =>
          p.name.toLowerCase().includes(term) ||
          p.pid.toString().includes(term)
      );
    }

    // CPU filter
    if (minCpu > 0) {
      filtered = filtered.filter((p) => p.cpuPercent >= minCpu);
    }

    // Memory filter
    if (minMemory > 0) {
      const minMemoryBytes = minMemory * 1024 * 1024; // Convert MB to bytes
      filtered = filtered.filter((p) => p.memoryBytes >= minMemoryBytes);
    }

    // Sort
    filtered.sort((a, b) => {
      let comparison = 0;

      switch (sortBy) {
        case 'cpu':
          comparison = a.cpuPercent - b.cpuPercent;
          break;
        case 'memory':
          comparison = a.memoryBytes - b.memoryBytes;
          break;
        case 'threads':
          comparison = a.threadCount - b.threadCount;
          break;
        case 'handles':
          comparison = a.handleCount - b.handleCount;
          break;
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'pid':
          comparison = a.pid - b.pid;
          break;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return filtered;
  }, [processes, searchTerm, minCpu, minMemory, sortBy, sortOrder]);

  return filteredProcesses;
};

/**
 * Hook to manage process filter state
 */
export const useProcessFilterState = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [minCpu, setMinCpu] = useState(0);
  const [minMemory, setMinMemory] = useState(0);
  const [sortBy, setSortBy] = useState<FilterOptions['sortBy']>('cpu');
  const [sortOrder, setSortOrder] = useState<FilterOptions['sortOrder']>('desc');

  const reset = () => {
    setSearchTerm('');
    setMinCpu(0);
    setMinMemory(0);
    setSortBy('cpu');
    setSortOrder('desc');
  };

  return {
    filters: { searchTerm, minCpu, minMemory, sortBy, sortOrder },
    setSearchTerm,
    setMinCpu,
    setMinMemory,
    setSortBy,
    setSortOrder,
    reset,
  };
};
