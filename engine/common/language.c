/*
language.c - helper to detect localization resources
Implement COM_LanguageExists to centralize detection of localization resources
Copyright (C) 2025 Vladislav Sukhov

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

#include "build.h"
#include "common.h"
#include "filesystem.h"
#include "crtlib.h"

// prefixes used to detect resource files
static const char *g_langPrefixes[] = { "valve", "gameui", "mainui" };

qboolean COM_LanguageExists( const char *lang )
{
	if( !lang || !lang[0] ) return false;

	// 1) search in gamedir for resource/*_<lang>.txt
	{
		char pattern[256];
		Q_snprintf( pattern, sizeof(pattern), "resource/*_%s.txt", lang );
		search_t *t = FS_Search( pattern, true, true );
		if( t && t->numfilenames > 0 )
		{
			Mem_Free( t );
			return true;
		}
		if( t ) Mem_Free( t );
	}

	// 2) search top-level gamedir_<lang> style dirs and verify they contain resource/<prefix>_<lang>.txt
	{
		search_t *d = FS_Search( "*_*", true, false );
		if( d )
		{
			int i, j;
			for( i = 0; i < d->numfilenames; i++ )
			{
				const char *found = d->filenames[i];
				const char *p = strrchr( found, '/' );
				if( !p ) p = found; else p++;
				const char *us = strchr( p, '_' );
				if( !us ) continue;
				const char *langpart = us + 1;
				const char *dot = strrchr( p, '.' );
				const char *end = dot ? dot : p + Q_strlen( p );
				int len = end - langpart;
				if( len <= 0 || len >= 128 ) continue;
				if( Q_strnicmp( langpart, lang, len ) ) continue;

				// check typical prefixes and gamefolder
				for( j = 0; j < (int)( sizeof(g_langPrefixes)/sizeof(g_langPrefixes[0]) ); j++ )
				{
					char checkpath[MAX_SYSPATH];
					Q_snprintf( checkpath, sizeof(checkpath), "%s/resource/%s_%s.txt", p, g_langPrefixes[j], lang );
					if( FS_FileExists( checkpath, false ) ) { Mem_Free( d ); return true; }
				}
				// also check GI->gamefolder
				{
					char checkpath[MAX_SYSPATH];
					Q_snprintf( checkpath, sizeof(checkpath), "%s/resource/%s_%s.txt", p, GI->gamefolder, lang );
					if( FS_FileExists( checkpath, false ) ) { Mem_Free( d ); return true; }
				}
			}
			Mem_Free( d );
		}
	}

	return false;
}
