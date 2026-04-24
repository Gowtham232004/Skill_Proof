'use client';
import { useEffect, useState } from 'react';
import axios from 'axios';

interface Gap {
  id: string;
  dimension: string;
  severity: 'CRITICAL' | 'IMPORTANT' | 'MINOR';
  name: string;
  description: string;
  fix: string;
  resource: string;
  estimatedHours: number;
}

interface GapReport {
  hasReport: boolean;
  overallHealthScore: number;
  criticalCount: number;
  importantCount: number;
  minorCount: number;
  gaps: Gap[];
}

const severityColors = {
  CRITICAL: 'border-red-500 bg-red-500/10',
  IMPORTANT: 'border-yellow-500 bg-yellow-500/10',
  MINOR: 'border-gray-500 bg-gray-500/10',
};

const severityText = {
  CRITICAL: 'text-red-400',
  IMPORTANT: 'text-yellow-400',
  MINOR: 'text-gray-400',
};

export default function SkillGapSection() {
  const [report, setReport] = useState<GapReport | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('sp_token') || localStorage.getItem('skillproof_token');
    if (!token) {
      setLoading(false);
      return;
    }

    // Poll for gap report (runs async after badge generation)
    const poll = async () => {
      try {
        const res = await axios.get(
          `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/gaps/latest`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        if (res.data.hasReport) {
          setReport(res.data);
          setLoading(false);
        } else {
          setTimeout(poll, 2000); // retry in 2s
        }
      } catch (error) {
        console.error('Gap report fetch failed:', error);
        setLoading(false);
      }
    };

    setTimeout(poll, 1500); // start after 1.5s delay
  }, []);

  if (loading) return (
    <div className="mt-8 text-center text-gray-500 font-mono text-sm animate-pulse">
      Scanning code for skill gaps...
    </div>
  );

  if (!report?.hasReport) return null;

  return (
    <div className="mt-10">
      {/* Health Score */}
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-sm font-mono text-[#D4FF00] tracking-widest uppercase">
          Skill Gap Report
        </h3>
        <div className="flex items-center gap-2">
          <span className="text-gray-400 text-sm font-mono">Code Health:</span>
          <span className={`font-mono font-bold text-lg ${
            report.overallHealthScore >= 70 ? 'text-green-400' :
            report.overallHealthScore >= 40 ? 'text-yellow-400' : 'text-red-400'
          }`}>
            {report.overallHealthScore}/100
          </span>
        </div>
      </div>

      {/* Gap count summary */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        {[
          { label: 'Critical', count: report.criticalCount, color: 'text-red-400' },
          { label: 'Important', count: report.importantCount, color: 'text-yellow-400' },
          { label: 'Minor', count: report.minorCount, color: 'text-gray-400' },
        ].map(item => (
          <div key={item.label} className="border border-white/10 rounded p-3 text-center">
            <div className={`font-mono font-bold text-2xl ${item.color}`}>{item.count}</div>
            <div className="text-gray-500 text-xs mt-1">{item.label}</div>
          </div>
        ))}
      </div>

      {/* Gap cards - show critical first */}
      {report.gaps.length === 0 ? (
        <div className="border border-green-500/30 bg-green-500/10 rounded p-4 text-green-400 font-mono text-sm">
          ✓ No major gaps detected. Your code follows good engineering practices.
        </div>
      ) : (
        <div className="space-y-3">
          {report.gaps.map((gap: Gap) => (
            <div
              key={gap.id}
              className={`border rounded p-4 ${severityColors[gap.severity]}`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <span className={`text-xs font-mono font-bold ${severityText[gap.severity]}`}>
                    {gap.severity}
                  </span>
                  <h4 className="text-white font-mono text-sm mt-1">{gap.name}</h4>
                  <p className="text-gray-400 text-xs mt-1">{gap.description}</p>
                </div>
                <span className="text-gray-500 text-xs font-mono whitespace-nowrap ml-4">
                  ~{gap.estimatedHours}h fix
                </span>
              </div>
              <div className="mt-3 pt-3 border-t border-white/10">
                <p className="text-[#D4FF00] text-xs font-mono">→ {gap.fix}</p>
                <p className="text-gray-500 text-xs mt-1">📖 {gap.resource}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
