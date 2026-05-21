/*
in_gyro.c - System gyroscope input code
Copyright (C) 2026 Xash3D FWGS contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

#include "common.h"
#include "input.h"
#include "client.h"

static CVAR_DEFINE_AUTO( gyro_enable, "0", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "enables aiming with built-in device gyroscope" );
static CVAR_DEFINE_AUTO( gyro_available, "0", FCVAR_READ_ONLY, "tells whether system gyroscope hardware is available or not" );
static CVAR_DEFINE_AUTO( gyro_pitch, "1.0", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope sensitivity for looking up and down" );
static CVAR_DEFINE_AUTO( gyro_yaw, "1.0", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope sensitivity for turning left and right" );
static CVAR_DEFINE_AUTO( gyro_roll, "0.0", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope sensitivity when tilting the device sideways" );
static CVAR_DEFINE_AUTO( gyro_pitch_deadzone, "0.5", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope pitch axis deadzone (deg/s)" );
static CVAR_DEFINE_AUTO( gyro_yaw_deadzone, "0.5", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope yaw axis deadzone (deg/s)" );
static CVAR_DEFINE_AUTO( gyro_roll_deadzone, "0.5", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "built-in gyroscope roll axis deadzone (deg/s)" );
static CVAR_DEFINE_AUTO( gyro_log_raw, "0", FCVAR_ARCHIVE | FCVAR_FILTERABLE, "enable raw gyroscope debug logging" );

// stores the latest instantaneous rotation rates from built-in gyroscope
static vec3_t gyro_speed;

/*
==============
IN_GyroInit

==============
*/
void IN_GyroInit( void )
{
	Cvar_RegisterVariable( &gyro_enable );
	Cvar_RegisterVariable( &gyro_available );
	Cvar_RegisterVariable( &gyro_pitch );
	Cvar_RegisterVariable( &gyro_yaw );
	Cvar_RegisterVariable( &gyro_roll );
	Cvar_RegisterVariable( &gyro_pitch_deadzone );
	Cvar_RegisterVariable( &gyro_yaw_deadzone );
	Cvar_RegisterVariable( &gyro_roll_deadzone );
	Cvar_RegisterVariable( &gyro_log_raw );

}

/*
==============
IN_GyroCheckAvailability

One-time late check called after startup and configs
==============
*/
void IN_GyroCheckAvailability( void )
{
#if XASH_SDL
	if( gyro_available.value )
		return;

	if( SDLash_GyroIsAvailable() )
	{
		Cvar_FullSet( "gyro_available", "1", FCVAR_READ_ONLY );
	}
#endif
}

static void IN_GyroMap( const vec3_t raw, platform_orientation_t orient, float *out_pitch, float *out_yaw, float *out_roll )
{
	float orient_scale = orient == ORIENTATION_LANDSCAPE_FLIPPED ? -1.0f : 1.0f;
	vec3_t sensor = { raw[0], raw[1], raw[2] };

	float pitch_speed;
	float yaw_speed;

#if XASH_PSVITA
	// PS Vita mapping: gyro X -> pitch, gyro Y -> yaw.
	pitch_speed = orient_scale * sensor[0] * (180.0f / M_PI);
	yaw_speed   = orient_scale * sensor[1] * (180.0f / M_PI);
#else
	// Android/iOS mapping: gyro Y -> pitch, gyro X -> yaw.
	pitch_speed = -orient_scale * sensor[1] * (180.0f / M_PI);
	yaw_speed   = orient_scale * sensor[0] * (180.0f / M_PI);
#endif
	float roll_speed = orient_scale * sensor[2] * (180.0f / M_PI);

	*out_pitch = pitch_speed;
	*out_yaw = yaw_speed;
	*out_roll = roll_speed;
}

/*
=============
IN_GyroEvent

System gyroscope events from platform
=============
*/
void IN_GyroEvent( vec3_t data )
{
	VectorCopy( data, gyro_speed );

	if( gyro_log_raw.value )
	{
		platform_orientation_t orient = Platform_GetDisplayOrientation();
		float pitch_speed, yaw_speed, roll_speed;
		IN_GyroMap( gyro_speed, orient, &pitch_speed, &yaw_speed, &roll_speed );
		Con_Printf( "gyro raw: x=%+.6f y=%+.6f z=%+.6f orient=%i -> pitch=%+.6f yaw=%+.6f roll=%+.6f\n",
			gyro_speed[0], gyro_speed[1], gyro_speed[2], (int)orient,
			pitch_speed, yaw_speed, roll_speed );
	}
}

/*
=============
IN_GyroFinalizeMove

Apply gyro movement to view angles
=============
*/
void IN_GyroFinalizeMove( float *fw, float *side, float *dpitch, float *dyaw )
{
	platform_orientation_t orient;

	if( !gyro_enable.value || !gyro_available.value )
		return;

	orient = Platform_GetDisplayOrientation();
	float pitch_speed, yaw_speed, roll_speed;
	IN_GyroMap( gyro_speed, orient, &pitch_speed, &yaw_speed, &roll_speed );

	if( fabs( pitch_speed ) < gyro_pitch_deadzone.value )
		pitch_speed = 0.0f;
	if( fabs( yaw_speed ) < gyro_yaw_deadzone.value )
		yaw_speed = 0.0f;
	if( fabs( roll_speed ) < gyro_roll_deadzone.value )
		roll_speed = 0.0f;

	*dpitch -= gyro_pitch.value * pitch_speed * host.realframetime;
	*dyaw   += gyro_yaw.value   * yaw_speed   * host.realframetime;
	*dyaw   += gyro_roll.value  * roll_speed  * host.realframetime;

	VectorClear( gyro_speed );
}

#if XASH_ENGINE_TESTS
void Test_RunInput( void )
{
	vec3_t raw = { 0.1f, 0.2f, 0.3f };
	float pitch_speed, yaw_speed, roll_speed;

	IN_GyroMap( raw, ORIENTATION_LANDSCAPE, &pitch_speed, &yaw_speed, &roll_speed );

	#if XASH_PSVITA
	TASSERT( Q_equal_e( pitch_speed, -0.1f * (180.0f / M_PI), 0.001f ) );
	TASSERT( Q_equal_e( yaw_speed,  0.2f * (180.0f / M_PI), 0.001f ) );
	#else
	TASSERT( Q_equal_e( pitch_speed, -0.2f * (180.0f / M_PI), 0.001f ) );
	TASSERT( Q_equal_e( yaw_speed,  0.1f * (180.0f / M_PI), 0.001f ) );
	#endif
	TASSERT( Q_equal_e( roll_speed, 0.3f * (180.0f / M_PI), 0.001f ) );
}
#endif
