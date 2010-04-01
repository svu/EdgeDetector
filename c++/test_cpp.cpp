#include <iostream>
#include <sstream>
#include <fstream>
#include <ctime>
#include <memory>
#include <cstddef>
#include <limits>
#include <string>
#include <assert.h>

#ifdef _MSC_VER
#include <ciso646>
#endif

#ifdef WIN32
#include <windows.h>
#undef max
#undef min

#else //WIN32

#error "windows is the only platform supported"

#endif//WIN32

namespace vc
{
	template < typename argument_type, typename result_type >
		class thread
	{
		typedef HANDLE handle_type;
		typedef DWORD id_type;
		struct thread_argument
		{
			thread_argument( result_type (*ThreadFunc)(argument_type*), argument_type* atArgument ) : ThreadFunc( ThreadFunc ), atArgument( atArgument ) {}
			result_type (*ThreadFunc)(argument_type*);
			argument_type* atArgument;
		};
	public:
		thread( result_type (*pThreadFunc) ( argument_type* ), argument_type* atArgument, const size_t stStackSize = 0 )
		{
			thread_argument* ptaCopyArgument = new thread_argument( pThreadFunc, atArgument );
			m_thtHandle = CreateThread( NULL, stStackSize, &function, (void*)ptaCopyArgument, CREATE_SUSPENDED, &m_tidThreadId );
		}
		bool Resume()
		{
			return ( ResumeThread( GetHandle() ) != DWORD(-1) );
		}
		static DWORD WINAPI function( void* pVoidArgument )
		{
			thread_argument& taCopyArgument = *reinterpret_cast< thread_argument* >( pVoidArgument );
			(*taCopyArgument.ThreadFunc)( taCopyArgument.atArgument );
			delete &taCopyArgument;
			return 0;
		}
		handle_type GetHandle() const { return m_thtHandle; }
		id_type GetID() const { return m_tidThreadId; }
	private:
		thread( const thread& ) {}
		thread& operator=( const thread& ) {}
		handle_type m_thtHandle;
		id_type m_tidThreadId;
	};
	typedef unsigned char char_type;
	typedef unsigned long long int64_type;
	typedef std::basic_istream<char_type> istream_type;
	typedef std::basic_ostream<char_type> ostream_type;
	typedef std::basic_ifstream<char_type> ifstream_type;
	typedef std::basic_ofstream<char_type> ofstream_type;
	typedef std::basic_stringstream<char_type> sstream_type;
	typedef std::basic_string<char_type> string_type;
	const static WORD g_wBitmapType = 0x4D42;

	inline istream_type& operator>>( istream_type& isStream, BITMAPFILEHEADER& bhBitmapHeader )
	{
		isStream.read( reinterpret_cast<char_type*>( &bhBitmapHeader ), sizeof( bhBitmapHeader ) );
		if( isStream )
		{
			if( bhBitmapHeader.bfType == g_wBitmapType )
            {
            }
			else
			{
				std::cout << "Unknown file format" << std::endl;
				isStream.setstate( std::ios::failbit );
			}
		}
		return isStream;
	}

	inline ostream_type& operator<<( ostream_type& osStream, BITMAPFILEHEADER& bhBitmapHeader )
	{
		osStream.write( reinterpret_cast<char_type*>( &bhBitmapHeader ), sizeof( bhBitmapHeader ) );
		return osStream;
	}

	inline istream_type& operator>>( istream_type& isStream, BITMAPINFOHEADER& bhBitmapInfo )
	{
		isStream.read( reinterpret_cast<char_type*>( &bhBitmapInfo ), sizeof( bhBitmapInfo ) );
		return isStream;
	}

	inline ostream_type& operator<<( ostream_type& osStream, BITMAPINFOHEADER& bhBitmapInfo )
	{
		osStream.write( reinterpret_cast<char_type*>( &bhBitmapInfo ), sizeof( bhBitmapInfo ) );
		return osStream;
	}

	template <typename pixel_t>
		void convert_bmp( const pixel_t* ctInputBuffer, char_type* ctOutputBuffer, const int nCharsPerPixel, const size_t stPitch, const size_t stWidth, size_t biHeightFrom, size_t biHeightTo );

	template < typename char_t>
		inline const char_t saturate( int nValue )
	{
		return std::min( std::max( nValue, 0 ), int( std::numeric_limits<char_type>::max() ) );
	}

	template < typename integer_t >
		inline integer_t inline_abs( integer_t tValue )
	{
		return tValue > 0 ? tValue : -tValue;
	}

	template<typename element_t >
		class auto_array_ptr
	{
	public:
		typedef element_t element_type;
		typedef element_type* pointer_type;
		explicit auto_array_ptr( element_type* p = 0 )
		: m_p( p )
		{
		}
		pointer_type get() const
		{
			return m_p;
		}
		~auto_array_ptr()
		{
			delete [] m_p;
		}
	private:
		pointer_type m_p;
	};


	template <>
		void convert_bmp( const char_type* ctInputBuffer, char_type* ctOutputBuffer, const int nCharsPerPixel, const size_t stPitch, const size_t stWidth, size_t biHeightFrom, size_t biHeightTo )
	{
		assert(std::numeric_limits<char_type>::digits == 8);
		const char_type* ctOriginalInput = ctInputBuffer;
		ctInputBuffer += biHeightFrom * stPitch;
		const size_t stDoublePitch = stPitch << 1;
		const int nDoubleCharsPerPixel = nCharsPerPixel << 1;

		for( size_t stY = biHeightFrom; stY < biHeightTo; ++stY, ctInputBuffer += stPitch )
		{
			const char_type* ctXInputBuffer = ctInputBuffer + nCharsPerPixel;
			for( size_t stX = 1; stX < stWidth - 1; ++stX )//, ctXInputBuffer += nCharsPerPixel
			{
				for( int nColour = 0; nColour < nCharsPerPixel; ++nColour, ++ctXInputBuffer )
				{
					int ctColourH;
					{
						const char_type* ctPlusInputBuffer = ctXInputBuffer + nCharsPerPixel - stPitch;
						const char_type* ctMinusInputBuffer = ctXInputBuffer - nCharsPerPixel - stPitch;
						ctColourH = inline_abs( ctPlusInputBuffer[ 0 ] + ( ctPlusInputBuffer[ stPitch ] << 1 ) + ctPlusInputBuffer[ stDoublePitch ] \
						        - ctMinusInputBuffer[ 0 ] - ( ctMinusInputBuffer[ stPitch ] << 1 ) - ctMinusInputBuffer[ stDoublePitch ] );
					}

					int ctColourV;
					{
						const char_type* ctPlusInputBuffer = ctXInputBuffer + stPitch - nCharsPerPixel;
						const char_type* ctMinusInputBuffer = ctXInputBuffer - stPitch - nCharsPerPixel;
						ctColourV = inline_abs( ctPlusInputBuffer[ 0 ] + ( ctPlusInputBuffer[ nCharsPerPixel ] << 1 ) + ctPlusInputBuffer[ nDoubleCharsPerPixel ] \
						        - ctMinusInputBuffer[ 0 ] - ( ctMinusInputBuffer[ nCharsPerPixel ] << 1 ) - ctMinusInputBuffer[ nDoubleCharsPerPixel ] );
					}
					ctOutputBuffer[ ctXInputBuffer - ctOriginalInput ] = saturate< char_type >( std::max( ctColourH, ctColourV ) );
				}
			}
		}
	}
}

using namespace vc;


struct convert_buffer
{
	const char_type* ctInputBuffer;
	char_type* ctOutputBuffer;
	int nCharsPerPixel;
	size_t stPitch;
	size_t stWidth;
	size_t biHeightFrom;
	size_t biHeightTo;
};

int tfThreadFunc( convert_buffer* pArg )
{
	convert_bmp( pArg->ctInputBuffer, pArg->ctOutputBuffer, pArg->nCharsPerPixel, pArg->stPitch, pArg->stWidth, pArg->biHeightFrom, pArg->biHeightTo );
	return 0;
}

void Usage()
{
	std::cout << "Usage: " << std::endl << "\t" << PRORGAM_NAME << " input_file.bmp [output_file.bmp [repeat_count [num_threads]] ]" << std::endl;
}
int main( int nArgc, char* sArgV[] )
{
	std::string sInFile;
	if( nArgc < 2 )
	{
		Usage();
		return 0;
	}
	sInFile = sArgV[1];

	std::string sOutFile = sInFile + ".out.bmp";
	if( nArgc >= 3 )
		sOutFile = sArgV[2];

	size_t stRepeats = 100;
	if( nArgc >= 4 )
	{
		std::istringstream osRepeatCount( sArgV[3] );
		osRepeatCount >> stRepeats;
	}

	size_t stThreads = 8;
	if( nArgc >= 5 )
	{
		std::istringstream osThreadsCount( sArgV[4] );
		osThreadsCount >> stThreads;
	}
	
	ifstream_type ifFile( sInFile.c_str(), std::ios_base::in | std::ios_base::binary );
	if( not ifFile )
	{
		std::cout << "failed to open: " << sInFile << std::endl;
		return -1;
	}
	BITMAPFILEHEADER bhBitmapHeader;
	ifFile >> bhBitmapHeader;

	BITMAPINFOHEADER bhBitmapInfo;
	ifFile >> bhBitmapInfo;

	size_t stCurrentPos = ifFile.tellg();
	if( stCurrentPos != bhBitmapHeader.bfOffBits )
	{
		std::cout << "Unsupported bitmap: " << ifFile << std::endl;
		return -1;
	}
	auto_array_ptr<char_type> pBuffer = auto_array_ptr<char_type>( new char_type[ bhBitmapInfo.biSizeImage ] );
	ifFile.read( pBuffer.get(), bhBitmapInfo.biSizeImage );
	
	auto_array_ptr<char_type> pOutputBuffer = auto_array_ptr<char_type>( new char_type[ bhBitmapInfo.biSizeImage ] );

	clock_t ctStartTime = clock();
	std::cout << "statring" << std::endl;

	const size_t stPitch = (( bhBitmapInfo.biWidth * bhBitmapInfo.biBitCount + 31 )/ 32) << 2;
	const int nCharsPerPixel = bhBitmapInfo.biBitCount / std::numeric_limits<char_type>::digits;

	auto_array_ptr<HANDLE> apHandles = auto_array_ptr<HANDLE>( new HANDLE[ stThreads ]);
	auto_array_ptr<convert_buffer> cbBuffers( new convert_buffer [ stThreads ] );
	auto_array_ptr<std::auto_ptr<thread<convert_buffer, int>> > apThreads( new std::auto_ptr<thread<convert_buffer, int>>[ stThreads ] );


	const size_t stRowsCount = ( bhBitmapInfo.biHeight - 2  + stThreads - 1 ) / stThreads;
	{
		size_t stCurrentRow = 1;
		for( size_t stCtr = 0; stCtr < stThreads; ++stCtr, stCurrentRow += stRowsCount )
		{
			cbBuffers.get()[ stCtr ].ctInputBuffer = pBuffer.get();
			cbBuffers.get()[ stCtr ].ctOutputBuffer = pOutputBuffer.get();
			cbBuffers.get()[ stCtr ].nCharsPerPixel = nCharsPerPixel;
			cbBuffers.get()[ stCtr ].stPitch = stPitch;
			cbBuffers.get()[ stCtr ].stWidth = bhBitmapInfo.biWidth;
			cbBuffers.get()[ stCtr ].biHeightFrom = stCurrentRow;
			cbBuffers.get()[ stCtr ].biHeightTo = std::min( stCurrentRow + stRowsCount, size_t( bhBitmapInfo.biHeight - 1 ) );
			//std::cout << "Thread " << stCtr << " will process from " << cbBuffers.get()[ stCtr ].biHeightFrom << " to " << cbBuffers.get()[ stCtr ].biHeightTo << std::endl;
		}
	}

	for( size_t stRepeat = 0; stRepeat < stRepeats; ++stRepeat)
	{
		for( size_t stCtr = 0; stCtr < stThreads; ++stCtr )
		{
			apThreads.get()[ stCtr ] = std::auto_ptr<thread<convert_buffer, int>>( new thread<convert_buffer, int>( tfThreadFunc, &cbBuffers.get()[ stCtr ] ) );
			apHandles.get()[ stCtr ] = apThreads.get()[ stCtr ].get()->GetHandle();
			apThreads.get()[ stCtr ].get()->Resume();
		}
		DWORD dResult = WaitForMultipleObjects( stThreads, apHandles.get(), TRUE, INFINITE);
	}

	clock_t ctElapsedTime = clock() - ctStartTime;
	std::cout << "finished in: " << float(ctElapsedTime) / CLOCKS_PER_SEC << std::endl;
	ofstream_type ofFile( sOutFile.c_str(), std::ios_base::out | std::ios_base::binary );

	ofFile << bhBitmapHeader;
	ofFile << bhBitmapInfo;
	ofFile.write( pOutputBuffer.get(), bhBitmapInfo.biSizeImage );
    return 0;
}
