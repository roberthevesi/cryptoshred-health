import { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X, Stethoscope, Building2 } from 'lucide-react';
import apiClient from '../lib/axios';
import type { GP } from '../types';

interface Props {
  value?: string;
  onChange: (gpId: string | undefined, gp?: GP) => void;
  className?: string;
  placeholder?: string;
  compact?: boolean;
}

export default function GpSelector({
  value,
  onChange,
  className = '',
  placeholder = 'Search & select a GP from directory...',
  compact = false,
}: Props) {
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [selectedGp, setSelectedGp] = useState<GP | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm), 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  // Fetch GPs
  const { data: gps = [], isLoading } = useQuery<GP[]>({
    queryKey: ['gps', debouncedSearch],
    queryFn: () =>
      apiClient
        .get<GP[]>('/gps', { params: debouncedSearch ? { search: debouncedSearch } : undefined })
        .then((r) => r.data),
    enabled: isOpen,
  });

  // Fetch selected GP details when value is set externally
  const { data: selectedGpData } = useQuery<GP>({
    queryKey: ['gp', value],
    queryFn: () => apiClient.get<GP>(`/gps/${value}`).then((r) => r.data),
    enabled: !!value && !selectedGp && value.length === 36, // UUID check
  });

  useEffect(() => {
    if (selectedGpData && !selectedGp) {
      setSelectedGp(selectedGpData);
    }
  }, [selectedGpData, selectedGp]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const handleSelect = (gp: GP) => {
    setSelectedGp(gp);
    onChange(gp.id, gp);
    setSearchTerm('');
    setIsOpen(false);
  };

  const handleClear = () => {
    setSelectedGp(null);
    onChange(undefined, undefined);
    setSearchTerm('');
  };

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Selected GP display or search input */}
      {selectedGp && !isOpen ? (
        <div className={`flex items-center gap-2.5 rounded-xl border border-blue-200 bg-blue-50/60 ${compact ? 'px-3 py-1.5' : 'px-4 py-2.5'}`}>
          <Stethoscope className="h-4 w-4 text-blue-600 shrink-0" />
          <div className="flex-1 min-w-0 flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-slate-900 truncate">
              Dr. {selectedGp.firstName} {selectedGp.lastName}
            </span>
            <div className="flex items-center gap-2 shrink-0">
              <span className="text-[11px] font-mono text-slate-600 bg-white border border-blue-200 px-1.5 py-0.5 rounded">
                GMC: {selectedGp.gmcNumber}
              </span>
              {selectedGp.practiceName && (
                <span className="text-[11px] text-slate-500 hidden sm:inline truncate max-w-[140px]">
                  {selectedGp.practiceName}
                </span>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={handleClear}
            className="p-1 rounded-lg hover:bg-blue-100 text-slate-400 hover:text-slate-700 transition-colors"
            title="Change GP"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : (
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setIsOpen(true);
            }}
            onFocus={() => setIsOpen(true)}
            placeholder={placeholder}
            className={`w-full rounded-xl border border-slate-300 bg-white pl-9 pr-4 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all ${
              compact ? 'py-1.5' : 'py-2.5'
            }`}
          />
        </div>
      )}

      {/* Dropdown Menu */}
      {isOpen && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl max-h-60 overflow-y-auto z-50 divide-y divide-slate-100">
          {isLoading ? (
            <div className="flex items-center justify-center gap-2 px-4 py-5 text-xs text-slate-500">
              <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
              Searching GP directory...
            </div>
          ) : gps.length === 0 ? (
            <div className="px-4 py-4 text-center text-xs text-slate-500">
              <p className="font-medium text-slate-700">No GPs found matching "{searchTerm}"</p>
              <p className="text-[11px] text-slate-400 mt-0.5">You can register GPs in the GP Directory tab.</p>
            </div>
          ) : (
            gps.map((gp) => (
              <button
                key={gp.id}
                type="button"
                onClick={() => handleSelect(gp)}
                className={`w-full text-left px-3.5 py-2.5 hover:bg-blue-50/50 cursor-pointer transition-colors ${
                  value === gp.id ? 'bg-blue-50' : ''
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-blue-50 border border-blue-200 text-blue-600">
                    <Stethoscope className="h-3.5 w-3.5" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-semibold text-slate-900">
                        Dr. {gp.firstName} {gp.lastName}
                        {gp.specialisation && (
                          <span className="text-[11px] font-normal text-slate-500 ml-1.5">
                            ({gp.specialisation})
                          </span>
                        )}
                      </p>
                      <span className="text-[10px] font-mono text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                        GMC {gp.gmcNumber}
                      </span>
                    </div>
                    {gp.practiceName && (
                      <div className="flex items-center gap-1 text-[11px] text-slate-500 mt-0.5">
                        <Building2 className="h-3 w-3 text-slate-400" />
                        <span className="truncate">{gp.practiceName}</span>
                      </div>
                    )}
                  </div>
                </div>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
