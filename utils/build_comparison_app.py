#!/usr/bin/env python3
"""
Master CLI Entry Point for CryptoShred Health Visual Comparison HTML Application.
Generates:
1. Primary: /Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/thesis/before_after_comparison.html
2. Artifact Copy: /Users/roberthevesi/.gemini/antigravity/brain/aef2e756-d39e-49f4-a059-1fc2f769547e/before_after_comparison.html
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from builder_parts.main import build_full_application

if __name__ == "__main__":
    build_full_application()
