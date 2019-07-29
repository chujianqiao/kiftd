package kohgylw.kiftd.server.mapper;

import kohgylw.kiftd.server.model.Users;
import org.apache.ibatis.annotations.Param;

public interface UsersMapper
{
    Users queryByUsername(final String USERNAME);

    int updateSizeByUsername(@Param("FILESIZE")final String FILESIZE, @Param("USERNAME")final String USERNAME);
}
