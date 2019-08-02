package kohgylw.kiftd.server.mapper;

import kohgylw.kiftd.server.model.*;
import org.apache.ibatis.annotations.Param;

import java.util.*;

public interface FolderMapper
{
    Folder queryById(final String fid);
    
    List<Folder> queryByParentId(final String pid);

    List<Folder> queryByAccount(@Param("fid")final String fid, @Param("account")final String account);

    List<Folder> queryByParentIdNOShare(@Param("fid")final String fid, @Param("typeid")final Integer typeid);
    
    Folder queryByParentIdAndFolderName(final Map<String, String> map);
    
    int insertNewFolder(final Folder f);
    
    int deleteById(final String folderId);
    
    int updateFolderNameById(final Map<String, String> map);

    int updateFolderSizeById(@Param("folderSize")final String folderSize, @Param("folderId")final String folderId);
    
    int updateFolderConstraintById(final Map<String, Object> map);

	int moveById(Map<String, String> map);
}
